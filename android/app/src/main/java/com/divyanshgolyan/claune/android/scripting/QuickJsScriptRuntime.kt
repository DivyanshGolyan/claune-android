package com.divyanshgolyan.claune.android.scripting

import com.divyanshgolyan.claune.android.data.local.SessionLogStore
import com.divyanshgolyan.claune.android.runtime.PhoneActuator
import com.divyanshgolyan.claune.android.runtime.PhoneObserver
import com.divyanshgolyan.claune.android.runtime.SessionCoordinator
import com.whl.quickjs.android.QuickJSLoader
import com.whl.quickjs.wrapper.JSCallFunction
import com.whl.quickjs.wrapper.QuickJSContext
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonNull

class QuickJsScriptRuntime(
    private val phoneObserver: PhoneObserver,
    private val phoneActuator: PhoneActuator,
    private val sessionCoordinator: SessionCoordinator,
    private val logStore: SessionLogStore,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val now: () -> Instant = { Instant.now() },
) : ScriptRuntime {
    init {
        ensureQuickJsLoaded()
    }

    override suspend fun execute(request: ScriptExecutionRequest): ScriptExecutionResult = withContext(dispatcher) {
        val startedAt = now()
        val scriptExecutionId = "script-${startedAt.toEpochMilli()}"
        val sessionId = sessionCoordinator.uiState.value.sessionId
        val host =
            ScriptHost(
                scriptExecutionId = scriptExecutionId,
                phoneObserver = phoneObserver,
                phoneActuator = phoneActuator,
                sessionCoordinator = sessionCoordinator,
                logStore = logStore,
                now = now,
            )

        sessionCoordinator.logEvent("Running script from ${request.source}.")

        val result =
            ScriptSourceValidator.firstUnsupportedFeature(request.script)?.let { unsupported ->
                ScriptExecutionResult(
                    ok = false,
                    summary = "Script uses unsupported syntax.",
                    error = unsupported.error,
                    hostCalls = emptyList(),
                )
            } ?: runCatching {
                executeScript(request, host)
            }.getOrElse { throwable ->
                ScriptExecutionResult(
                    ok = false,
                    summary = "Script execution failed.",
                    error = throwable.message ?: throwable::class.simpleName ?: "Unknown QuickJS failure.",
                    hostCalls = host.hostCalls(),
                )
            }

        val finishedAt = now()
        val record =
            ScriptExecutionRecord(
                scriptExecutionId = scriptExecutionId,
                sessionId = sessionId,
                source = request.source,
                script = request.script,
                ok = result.ok,
                summary = result.summary,
                dataJson = result.data?.let(ScriptJson::encodeElement),
                hostCallCount = result.hostCalls.size,
                error = result.error,
                startedAt = startedAt.toString(),
                finishedAt = finishedAt.toString(),
            )
        logStore.recordScriptExecution(record)
        sessionCoordinator.logEvent(result.summary)
        result
    }

    private fun executeScript(request: ScriptExecutionRequest, host: ScriptHost): ScriptExecutionResult {
        val context = QuickJSContext.create()
        val global = context.getGlobalObject()
        try {
            global.setProperty(
                "__clauneObservePhoneJson",
                JSCallFunction {
                    runBlocking {
                        ScriptJson.codec.encodeToString(
                            UiSnapshotPayload.serializer(),
                            host.observePhone(),
                        )
                    }
                },
            )
            global.setProperty(
                "__clauneTapElementJson",
                JSCallFunction { args: Array<out Any?> ->
                    val elementId = args.firstOrNull()?.toString().orEmpty()
                    runBlocking {
                        ScriptJson.codec.encodeToString(
                            HostCallOutcome.serializer(),
                            host.tapElement(elementId),
                        )
                    }
                },
            )
            global.setProperty(
                "__clauneTapRefJson",
                JSCallFunction { args: Array<out Any?> ->
                    val ref = args.firstOrNull()?.toString().orEmpty()
                    runBlocking {
                        ScriptJson.codec.encodeToString(
                            HostCallOutcome.serializer(),
                            host.tapRef(ref),
                        )
                    }
                },
            )
            global.setProperty(
                "__clauneTapSelectorJson",
                JSCallFunction { args: Array<out Any?> ->
                    val selectorJson = args.firstOrNull()?.toString().orEmpty()
                    runBlocking {
                        ScriptJson.codec.encodeToString(
                            HostCallOutcome.serializer(),
                            host.tapSelector(selectorJson),
                        )
                    }
                },
            )
            global.setProperty(
                "__clauneTypeIntoElementJson",
                JSCallFunction { args: Array<out Any?> ->
                    val elementId = args.getOrNull(0)?.toString().orEmpty()
                    val text = args.getOrNull(1)?.toString().orEmpty()
                    runBlocking {
                        ScriptJson.codec.encodeToString(
                            HostCallOutcome.serializer(),
                            host.typeIntoElement(elementId, text),
                        )
                    }
                },
            )
            global.setProperty(
                "__clauneTypeIntoSelectorJson",
                JSCallFunction { args: Array<out Any?> ->
                    val selectorJson = args.getOrNull(0)?.toString().orEmpty()
                    val text = args.getOrNull(1)?.toString().orEmpty()
                    runBlocking {
                        ScriptJson.codec.encodeToString(
                            HostCallOutcome.serializer(),
                            host.typeIntoSelector(selectorJson, text),
                        )
                    }
                },
            )
            global.setProperty(
                "__clauneTypeIntoFocusedJson",
                JSCallFunction { args: Array<out Any?> ->
                    val text = args.getOrNull(0)?.toString().orEmpty()
                    runBlocking {
                        ScriptJson.codec.encodeToString(
                            HostCallOutcome.serializer(),
                            host.typeIntoFocused(text),
                        )
                    }
                },
            )
            global.setProperty(
                "__clauneScrollContainerJson",
                JSCallFunction { args: Array<out Any?> ->
                    val elementId = args.getOrNull(0)?.toString().orEmpty()
                    val direction = args.getOrNull(1)?.toString().orEmpty()
                    runBlocking {
                        ScriptJson.codec.encodeToString(
                            HostCallOutcome.serializer(),
                            host.scrollContainer(elementId, direction),
                        )
                    }
                },
            )
            global.setProperty(
                "__claunePressBackJson",
                JSCallFunction {
                    runBlocking {
                        ScriptJson.codec.encodeToString(
                            HostCallOutcome.serializer(),
                            host.pressBack(),
                        )
                    }
                },
            )
            global.setProperty(
                "__claunePressHomeJson",
                JSCallFunction {
                    runBlocking {
                        ScriptJson.codec.encodeToString(
                            HostCallOutcome.serializer(),
                            host.pressHome(),
                        )
                    }
                },
            )
            global.setProperty(
                "__clauneWaitForStateJson",
                JSCallFunction { args: Array<out Any?> ->
                    val type = args.getOrNull(0)?.toString().orEmpty()
                    val value = args.getOrNull(1)?.toString().orEmpty()
                    val timeoutMs = args.getOrNull(2)?.toString()?.toLongOrNull() ?: 0L
                    runBlocking {
                        ScriptJson.codec.encodeToString(
                            HostCallOutcome.serializer(),
                            host.waitForState(type, value, timeoutMs),
                        )
                    }
                },
            )
            global.setProperty(
                "__clauneWaitForSelectorJson",
                JSCallFunction { args: Array<out Any?> ->
                    val selectorJson = args.getOrNull(0)?.toString().orEmpty()
                    val timeoutMs = args.getOrNull(1)?.toString()?.toLongOrNull() ?: 0L
                    runBlocking {
                        ScriptJson.codec.encodeToString(
                            HostCallOutcome.serializer(),
                            host.waitForSelector(selectorJson, timeoutMs),
                        )
                    }
                },
            )

            context.evaluate(ClauneHostContract.bootstrapJavascript)

            val resultJson = context.evaluate(wrapUserScript(request.script)) as? String

            val parsedData = resultJson?.let(ScriptJson.codec::parseToJsonElement) ?: JsonNull
            val summary =
                buildString {
                    append("Script ")
                    append(
                        if (host.hostCalls().isEmpty()) {
                            "completed without host calls."
                        } else {
                            "completed with ${host.hostCalls().size} host calls."
                        },
                    )
                }

            return ScriptExecutionResult(
                ok = true,
                summary = summary,
                data = parsedData,
                hostCalls = host.hostCalls(),
            )
        } finally {
            context.destroy()
        }
    }

    private fun wrapUserScript(script: String): String =
        """
        (() => {
          const __clauneUserResult = (() => {
        $script
          })();
          return JSON.stringify(__clauneUserResult === undefined ? null : __clauneUserResult);
        })()
        """.trimIndent()

    private companion object {
        private val QUICK_JS_LOADED = AtomicBoolean(false)

        private fun ensureQuickJsLoaded() {
            if (QUICK_JS_LOADED.compareAndSet(false, true)) {
                QuickJSLoader.init()
            }
        }
    }
}

internal object ScriptSourceValidator {
    fun firstUnsupportedFeature(script: String): UnsupportedScriptFeature? = unsupportedFeatures.firstOrNull { feature ->
        feature.pattern.containsMatchIn(script)
    }

    private val unsupportedFeatures =
        listOf(
            UnsupportedScriptFeature(
                name = "await",
                pattern = Regex("""(^|[^\w$])await\s+"""),
                error = "unsupported_syntax: top-level await is not supported; claune APIs are synchronous plain function calls",
            ),
            UnsupportedScriptFeature(
                name = "async",
                pattern = Regex("""(^|[^\w$])async\s+"""),
                error = "unsupported_syntax: async functions are not supported; write synchronous scripts only",
            ),
            UnsupportedScriptFeature(
                name = "promise",
                pattern = Regex("""(^|[^\w$])Promise(\b|\.)"""),
                error = "unsupported_syntax: Promise syntax is not supported in Claune scripts",
            ),
            UnsupportedScriptFeature(
                name = "console",
                pattern = Regex("""(^|[^\w$])console\."""),
                error = "unsupported_api: console is not available in Claune scripts; return compact data instead",
            ),
            UnsupportedScriptFeature(
                name = "module",
                pattern = Regex("""(^|\n)\s*(import|export)\b"""),
                error = "unsupported_syntax: import/export modules are not supported in Claune scripts",
            ),
        )
}

internal data class UnsupportedScriptFeature(val name: String, val pattern: Regex, val error: String)
