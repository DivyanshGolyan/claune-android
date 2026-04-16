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
            runCatching {
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

            context.evaluate(CLAUNE_BOOTSTRAP)

            val resultJson =
                context.evaluate(
                    wrapUserScript(request.script),
                ) as? String

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
            global.release()
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

        private val CLAUNE_BOOTSTRAP =
            """
            globalThis.claune = Object.freeze({
              observePhone() {
                return JSON.parse(__clauneObservePhoneJson());
              },
              tapElement(elementId) {
                return JSON.parse(__clauneTapElementJson(String(elementId)));
              },
              typeIntoElement(elementId, text) {
                return JSON.parse(__clauneTypeIntoElementJson(String(elementId), String(text)));
              },
              scrollContainer(elementId, direction) {
                return JSON.parse(__clauneScrollContainerJson(String(elementId), String(direction)));
              },
              pressBack() {
                return JSON.parse(__claunePressBackJson());
              },
              pressHome() {
                return JSON.parse(__claunePressHomeJson());
              },
              waitForState(type, value, timeoutMs) {
                return JSON.parse(__clauneWaitForStateJson(String(type), String(value), Number(timeoutMs ?? 0)));
              }
            });
            """.trimIndent()

        private fun ensureQuickJsLoaded() {
            if (QUICK_JS_LOADED.compareAndSet(false, true)) {
                QuickJSLoader.init()
            }
        }
    }
}
