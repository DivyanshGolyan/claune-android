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
import org.mozilla.javascript.CompilerEnvirons
import org.mozilla.javascript.Context
import org.mozilla.javascript.Parser
import org.mozilla.javascript.ast.AstNode
import org.mozilla.javascript.ast.Name
import org.mozilla.javascript.ast.NodeVisitor
import org.mozilla.javascript.ast.PropertyGet

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
                    summary = if (throwable is ScriptValidationException) "Script uses unsupported syntax." else "Script execution failed.",
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
        try {
            registerHostFunctions(context, host)

            context.evaluate(ClauneHostContract.bootstrapJavascript)
            val compiledScript = compileUserScript(context, request.script)

            val resultJson = context.execute(compiledScript) as? String

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

    private fun compileUserScript(context: QuickJSContext, script: String): ByteArray = try {
        context.compile(wrapUserScript(script), "claune-user-script.js")
    } catch (throwable: Throwable) {
        throw ScriptValidationException(mapSyntaxError(script, throwable))
    }

    private fun registerHostFunctions(context: QuickJSContext, host: ScriptHost) {
        context.registerJsonFunction("__clauneObservePhoneJson") {
            encodeSnapshot(host.observePhone())
        }
        context.registerJsonFunction("__clauneTapElementJson") { args ->
            encodeOutcome(host.tapElement(args.stringArg(0)))
        }
        context.registerJsonFunction("__clauneTapRefJson") { args ->
            encodeOutcome(host.tapRef(args.stringArg(0)))
        }
        context.registerJsonFunction("__clauneScrollRefJson") { args ->
            encodeOutcome(host.scrollRef(args.stringArg(0), args.stringArg(1)))
        }
        context.registerJsonFunction("__clauneFocusSelectorJson") { args ->
            encodeOutcome(host.focusSelector(args.stringArg(0), args.longArg(1)))
        }
        context.registerJsonFunction("__clauneTapSelectorJson") { args ->
            encodeOutcome(host.tapSelector(args.stringArg(0)))
        }
        context.registerJsonFunction("__clauneTypeIntoElementJson") { args ->
            encodeOutcome(host.typeIntoElement(args.stringArg(0), args.stringArg(1)))
        }
        context.registerJsonFunction("__clauneTypeIntoSelectorJson") { args ->
            encodeOutcome(host.typeIntoSelector(args.stringArg(0), args.stringArg(1)))
        }
        context.registerJsonFunction("__clauneTypeIntoFocusedJson") { args ->
            encodeOutcome(host.typeIntoFocused(args.stringArg(0)))
        }
        context.registerJsonFunction("__clauneScrollContainerJson") { args ->
            encodeOutcome(host.scrollContainer(args.stringArg(0), args.stringArg(1)))
        }
        context.registerJsonFunction("__claunePressBackJson") {
            encodeOutcome(host.pressBack())
        }
        context.registerJsonFunction("__claunePressHomeJson") {
            encodeOutcome(host.pressHome())
        }
        context.registerJsonFunction("__clauneWaitForStateJson") { args ->
            encodeOutcome(host.waitForState(args.stringArg(0), args.stringArg(1), args.longArg(2)))
        }
        context.registerJsonFunction("__clauneWaitForSelectorJson") { args ->
            encodeOutcome(host.waitForSelector(args.stringArg(0), args.longArg(1)))
        }
    }

    private fun QuickJSContext.registerJsonFunction(name: String, callback: suspend (Array<out Any?>) -> String) {
        getGlobalObject().setProperty(
            name,
            JSCallFunction { args: Array<out Any?> ->
                runBlocking {
                    callback(args)
                }
            },
        )
    }

    private suspend fun encodeSnapshot(snapshot: UiSnapshotPayload): String =
        ScriptJson.codec.encodeToString(UiSnapshotPayload.serializer(), snapshot)

    private suspend fun encodeOutcome(outcome: HostCallOutcome): String =
        ScriptJson.codec.encodeToString(HostCallOutcome.serializer(), outcome)

    private fun Array<out Any?>.stringArg(index: Int): String = getOrNull(index)?.toString().orEmpty()

    private fun Array<out Any?>.longArg(index: Int): Long = getOrNull(index)?.toString()?.toLongOrNull() ?: 0L

    private fun mapSyntaxError(script: String, throwable: Throwable): String {
        val message = throwable.message?.trim()?.lineSequence()?.firstOrNull()?.trim().orEmpty()
        return when {
            script.contains("await") ->
                "unsupported_syntax: top-level await is not supported; claune APIs are synchronous plain function calls"

            script.contains("import ") || script.contains("\nimport ") || script.contains("export ") ->
                "unsupported_syntax: import/export modules are not supported in Claune scripts"

            script.contains("async ") ->
                "unsupported_syntax: async functions are not supported; write synchronous scripts only"

            message.isNotBlank() -> "unsupported_syntax: $message"
            else -> "unsupported_syntax: script could not be compiled by QuickJS"
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
    fun firstUnsupportedFeature(script: String): UnsupportedScriptFeature? {
        val ast =
            runCatching {
                Parser(
                    CompilerEnvirons().apply {
                        languageVersion = Context.VERSION_ES6
                    },
                ).parse(wrapForStaticAnalysis(script), "claune-user-script.js", 1)
            }.getOrNull() ?: return null

        var unsupported: UnsupportedScriptFeature? = null
        ast.visit(
            NodeVisitor { node ->
                if (unsupported != null) {
                    return@NodeVisitor false
                }
                unsupported = unsupportedFeatureForNode(node)
                unsupported == null
            },
        )
        return unsupported
    }

    private fun unsupportedFeatureForNode(node: AstNode): UnsupportedScriptFeature? = when {
        node is PropertyGet && (node.left as? Name)?.identifier == "console" ->
            UnsupportedScriptFeature(
                name = "console",
                error = "unsupported_api: console is not available in Claune scripts; return compact data instead",
            )

        node is Name && node.identifier == "Promise" ->
            UnsupportedScriptFeature(
                name = "promise",
                error = "unsupported_syntax: Promise syntax is not supported in Claune scripts",
            )

        else -> null
    }

    private fun wrapForStaticAnalysis(script: String): String =
        """
        function __clauneStaticAnalysis() {
        $script
        }
        """.trimIndent()
}

private class ScriptValidationException(message: String) : IllegalArgumentException(message)

internal data class UnsupportedScriptFeature(val name: String, val error: String)
