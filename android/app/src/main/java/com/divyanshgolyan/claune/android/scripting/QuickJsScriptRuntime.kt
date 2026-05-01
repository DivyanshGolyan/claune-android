package com.divyanshgolyan.claune.android.scripting

import com.divyanshgolyan.claune.android.data.local.PerfTelemetry
import com.divyanshgolyan.claune.android.data.local.SessionLogStore
import com.divyanshgolyan.claune.android.runtime.PhoneActuator
import com.divyanshgolyan.claune.android.runtime.PhoneObserver
import com.divyanshgolyan.claune.android.runtime.SessionCoordinator
import com.divyanshgolyan.claune.android.runtime.elapsedMs
import com.divyanshgolyan.claune.android.shell.ClauneJsResult
import com.divyanshgolyan.claune.android.shell.ClauneJsRunner
import com.whl.quickjs.android.QuickJSLoader
import com.whl.quickjs.wrapper.JSCallFunction
import com.whl.quickjs.wrapper.QuickJSContext
import java.io.File
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import org.mozilla.javascript.CompilerEnvirons
import org.mozilla.javascript.Context
import org.mozilla.javascript.Parser
import org.mozilla.javascript.ast.AstNode
import org.mozilla.javascript.ast.Name
import org.mozilla.javascript.ast.NodeVisitor

class QuickJsScriptRuntime(
    private val phoneObserver: PhoneObserver,
    private val phoneActuator: PhoneActuator,
    private val installedAppRegistry: InstalledAppRegistry = EmptyInstalledAppRegistry,
    private val sessionCoordinator: SessionCoordinator,
    private val logStore: SessionLogStore,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val now: () -> Instant = { Instant.now() },
) : ScriptRuntime,
    ClauneJsRunner {
    init {
        ensureQuickJsLoaded()
    }

    override suspend fun run(scriptPath: String, argv: List<String>, stdin: String): ClauneJsResult = withContext(dispatcher) {
        val scriptFile = File(scriptPath)
        val script =
            runCatching { scriptFile.readText() }
                .getOrElse { throwable ->
                    return@withContext ClauneJsResult(
                        exitCode = 1,
                        stdout = "",
                        stderr = "${throwable.message ?: "Unable to read script: $scriptPath"}\n",
                    )
                }

        runCliScriptSource(
            script = script,
            scriptName = scriptFile.name.ifBlank { "claune-script.js" },
            argv = argv,
            stdin = stdin,
        )
    }

    override suspend fun runInline(script: String, argv: List<String>, stdin: String): ClauneJsResult = withContext(dispatcher) {
        runCliScriptSource(
            script = script,
            scriptName = "stdin.js",
            argv = argv,
            stdin = stdin,
        )
    }

    override suspend fun help(topic: String?): ClauneJsResult = withContext(dispatcher) {
        ClauneJsResult(
            exitCode = 0,
            stdout = ClauneHostContract.cliHelp(topic).trimEnd() + "\n",
            stderr = "",
        )
    }

    private fun runCliScriptSource(script: String, scriptName: String, argv: List<String>, stdin: String): ClauneJsResult {
        val startedAt = now()
        val scriptExecutionId = "script-${startedAt.toEpochMilli()}"
        val host =
            ScriptHost(
                scriptExecutionId = scriptExecutionId,
                phoneObserver = phoneObserver,
                phoneActuator = phoneActuator,
                installedAppRegistry = installedAppRegistry,
                sessionCoordinator = sessionCoordinator,
                logStore = logStore,
                now = now,
            )

        val result =
            ScriptSourceValidator.firstUnsupportedFeature(script)?.let { unsupported ->
                ClauneJsResult(
                    exitCode = 1,
                    stdout = "",
                    stderr = "${unsupported.error}\n",
                    hostCalls = emptyList(),
                )
            } ?: runCatching {
                executeCliScript(
                    script = script,
                    scriptName = scriptName,
                    host = host,
                    argv = argv,
                    stdin = stdin,
                )
            }.getOrElse { throwable ->
                ClauneJsResult(
                    exitCode = 1,
                    stdout = "",
                    stderr = "${quickJsErrorMessage(throwable)}\n",
                    hostCalls = host.hostCalls(),
                )
            }
        return result
    }

    override suspend fun execute(request: ScriptExecutionRequest): ScriptExecutionResult = withContext(dispatcher) {
        val startedAt = now()
        val scriptExecutionId = "script-${startedAt.toEpochMilli()}"
        val runId = sessionCoordinator.uiState.value.activeRunId
        val host =
            ScriptHost(
                scriptExecutionId = scriptExecutionId,
                phoneObserver = phoneObserver,
                phoneActuator = phoneActuator,
                installedAppRegistry = installedAppRegistry,
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
                runId = runId,
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
            val compiledScript = compileUserScript(context, request.script, "claune-user-script.js")

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

    private fun executeCliScript(script: String, scriptName: String, host: ScriptHost, argv: List<String>, stdin: String): ClauneJsResult {
        val context = QuickJSContext.create()
        val stdout = StringBuilder()
        val stderr = StringBuilder()
        try {
            registerCliOutputFunctions(context, stdout, stderr)
            registerCliGlobals(context, argv, stdin)
            registerHostFunctions(context, host)

            context.evaluate(ClauneHostContract.bootstrapJavascript)
            val compiledScript = compileUserScript(context, script, scriptName, QuickJsCliContract::wrapScript)
            context.execute(compiledScript)

            return ClauneJsResult(
                exitCode = 0,
                stdout = stdout.toString(),
                stderr = stderr.toString(),
                hostCalls = host.hostCalls(),
            )
        } catch (throwable: Throwable) {
            stderr.append(quickJsErrorMessage(throwable)).append('\n')
            return ClauneJsResult(
                exitCode = 1,
                stdout = stdout.toString(),
                stderr = stderr.toString(),
                hostCalls = host.hostCalls(),
            )
        } finally {
            context.destroy()
        }
    }

    private fun compileUserScript(
        context: QuickJSContext,
        script: String,
        scriptName: String,
        wrapper: (String) -> String = ::wrapUserScript,
    ): ByteArray = try {
        context.compile(wrapper(script), scriptName)
    } catch (throwable: Throwable) {
        throw ScriptValidationException(mapSyntaxError(script, throwable))
    }

    private fun registerCliOutputFunctions(context: QuickJSContext, stdout: StringBuilder, stderr: StringBuilder) {
        context.getGlobalObject().setProperty(
            "__clauneWriteStdoutLine",
            JSCallFunction { args: Array<out Any?> ->
                stdout.append(args.stringArg(0)).append('\n')
                null
            },
        )
        context.getGlobalObject().setProperty(
            "__clauneWriteStderrLine",
            JSCallFunction { args: Array<out Any?> ->
                stderr.append(args.stringArg(0)).append('\n')
                null
            },
        )
        context.evaluate(
            QuickJsCliContract.outputBootstrapJavascript,
        )
    }

    private fun registerCliGlobals(context: QuickJSContext, argv: List<String>, stdin: String) {
        context.evaluate(QuickJsCliContract.globalsBootstrapJavascript(argv, stdin))
    }

    private fun registerHostFunctions(context: QuickJSContext, host: ScriptHost) {
        context.registerJsonFunction("__clauneObserveScreenJson") { args ->
            encodeScreenObservation(host, PerfTelemetry.OBSERVE_SCREEN_ENCODE, host.observeScreen(args.stringArg(0)))
        }
        context.registerJsonFunction("__clauneDiffScreenJson") { args ->
            encodeScreenObservation(host, PerfTelemetry.DIFF_SCREEN_ENCODE, host.diffScreen(args.stringArg(0)))
        }
        context.registerJsonFunction("__clauneInspectScreenJson") { args ->
            encodeInspection(host.inspectScreen(args.stringArg(0)))
        }
        context.registerJsonFunction("__clauneFindRawNodesJson") { args ->
            encodeRawNodeSearchResult(host.findRawNodes(args.stringArg(0)))
        }
        context.registerJsonFunction("__clauneListInstalledAppsJson") {
            encodeInstalledApps(host.listInstalledApps())
        }
        context.registerJsonFunction("__clauneLaunchAppJson") { args ->
            encodeOutcome(host.launchApp(args.stringArg(0)))
        }
        context.registerJsonFunction("__clauneTapElementJson") { args ->
            encodeOutcome(host.tapElement(args.stringArg(0)))
        }
        context.registerJsonFunction("__clauneTapRefJson") { args ->
            encodeOutcome(host.tapRef(args.stringArg(0)))
        }
        context.registerJsonFunction("__clauneTapTextJson") { args ->
            encodeOutcome(host.tapText(args.stringArg(0), args.booleanArg(1), args.booleanArg(2)))
        }
        context.registerJsonFunction("__clauneTapPointJson") { args ->
            encodeOutcome(host.tapPoint(args.intArg(0), args.intArg(1)))
        }
        context.registerJsonFunction("__clauneTapBoundsJson") { args ->
            encodeOutcome(host.tapBounds(args.stringArg(0)))
        }
        context.registerJsonFunction("__claunePerformActionJson") { args ->
            encodeOutcome(host.performAction(args.stringArg(0)))
        }
        context.registerJsonFunction("__clauneScrollRefJson") { args ->
            encodeOutcome(host.scrollRef(args.stringArg(0), args.stringArg(1)))
        }
        context.registerJsonFunction("__clauneScrollScreenJson") { args ->
            encodeOutcome(host.scrollScreen(args.stringArg(0)))
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

    private suspend fun encodeScreenObservation(host: ScriptHost, name: String, observation: ScreenObservationPayload): String {
        val started = System.nanoTime()
        val encoded = ScriptJson.codec.encodeToString(ScreenObservationPayload.serializer(), observation)
        host.recordPerfEvent(
            name = name,
            scope = PerfTelemetry.SCOPE_QUICKJS_BRIDGE,
            durationMs = elapsedMs(started),
            attrs =
            mapOf(
                "snapshotId" to observation.currentSnapshotId,
                "foregroundPackage" to observation.foregroundPackage,
                "mode" to observation.mode,
                "payloadBytes" to encoded.toByteArray().size.toString(),
                "elementCount" to observation.elements.size.toString(),
                "groupCount" to observation.groups.size.toString(),
                "actionCount" to observation.actions.size.toString(),
            ),
        )
        return encoded
    }

    private suspend fun encodeInstalledApps(apps: List<InstalledAppPayload>): String =
        ScriptJson.codec.encodeToString(ListSerializer(InstalledAppPayload.serializer()), apps)

    private suspend fun encodeInspection(inspection: ScreenInspectionPayload): String =
        ScriptJson.codec.encodeToString(ScreenInspectionPayload.serializer(), inspection)

    private suspend fun encodeRawNodeSearchResult(result: RawNodeSearchResultPayload): String =
        ScriptJson.codec.encodeToString(RawNodeSearchResultPayload.serializer(), result)

    private suspend fun encodeOutcome(outcome: HostCallOutcome): String =
        ScriptJson.codec.encodeToString(HostCallOutcome.serializer(), outcome)

    private fun Array<out Any?>.stringArg(index: Int): String = getOrNull(index)?.toString().orEmpty()

    private fun Array<out Any?>.intArg(index: Int): Int = getOrNull(index)?.toString()?.toDoubleOrNull()?.toInt() ?: 0

    private fun Array<out Any?>.longArg(index: Int): Long = getOrNull(index)?.toString()?.toLongOrNull() ?: 0L

    private fun Array<out Any?>.booleanArg(index: Int): Boolean = getOrNull(index)?.toString()?.toBooleanStrictOrNull() ?: false

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

    private fun quickJsErrorMessage(throwable: Throwable): String = throwable.message?.trim()?.takeIf(String::isNotBlank)
        ?: throwable::class.simpleName
        ?: "Unknown QuickJS failure."

    private companion object {
        private val QUICK_JS_LOADED = AtomicBoolean(false)

        private fun ensureQuickJsLoaded() {
            if (QUICK_JS_LOADED.compareAndSet(false, true)) {
                QuickJSLoader.init()
            }
        }
    }
}

internal object QuickJsCliContract {
    val outputBootstrapJavascript: String =
        """
        (() => {
          function __clauneFormatCliValue(value) {
            if (typeof value === "string") return value;
            if (typeof value === "undefined") return "undefined";
            try {
              const json = JSON.stringify(value);
              return typeof json === "undefined" ? String(value) : json;
            } catch (error) {
              return String(value);
            }
          }
          function __clauneJoinCliArgs(args) {
            return Array.prototype.map.call(args, __clauneFormatCliValue).join(" ");
          }
          globalThis.print = function() {
            __clauneWriteStdoutLine(__clauneJoinCliArgs(arguments));
          };
          globalThis.console = Object.freeze({
            log: function() { __clauneWriteStdoutLine(__clauneJoinCliArgs(arguments)); },
            error: function() { __clauneWriteStderrLine(__clauneJoinCliArgs(arguments)); }
          });
          globalThis.__clauneWriteCliReturn = function(value) {
            __clauneWriteStdoutLine(__clauneFormatCliValue(value));
          };
        })()
        """.trimIndent()

    fun globalsBootstrapJavascript(argv: List<String>, stdin: String): String {
        val argvJson = ScriptJson.codec.encodeToString(ListSerializer(String.serializer()), argv)
        val stdinJson = ScriptJson.codec.encodeToString(JsonPrimitive(stdin))
        return """
            globalThis.argv = $argvJson;
            globalThis.stdin = $stdinJson;
        """.trimIndent()
    }

    fun wrapScript(script: String): String =
        """
        (() => {
          const __clauneCliResult = (() => {
        $script
          })();
          if (__clauneCliResult !== undefined) {
            __clauneWriteCliReturn(__clauneCliResult);
          }
        })()
        """.trimIndent()
}

internal object ScriptSourceValidator {
    fun firstUnsupportedFeature(script: String): UnsupportedScriptFeature? {
        unsupportedKeywordFeature(script)?.let { return it }

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

    private fun unsupportedKeywordFeature(script: String): UnsupportedScriptFeature? {
        for (identifier in scriptIdentifiers(script)) {
            when (identifier) {
                "async" ->
                    return UnsupportedScriptFeature(
                        name = "async",
                        error = "unsupported_syntax: async functions are not supported; write synchronous scripts only",
                    )

                "await" ->
                    return UnsupportedScriptFeature(
                        name = "await",
                        error = "unsupported_syntax: top-level await is not supported; claune APIs are synchronous plain function calls",
                    )

                "import", "export" ->
                    return UnsupportedScriptFeature(
                        name = "module",
                        error = "unsupported_syntax: import/export modules are not supported in Claune scripts",
                    )
            }
        }
        return null
    }

    private fun scriptIdentifiers(script: String): Sequence<String> = sequence {
        var index = 0
        while (index < script.length) {
            val char = script[index]
            val next = script.getOrNull(index + 1)
            when {
                char == '/' && next == '/' -> {
                    index += 2
                    while (index < script.length && script[index] != '\n') index += 1
                }

                char == '/' && next == '*' -> {
                    index += 2
                    while (index < script.length && !(script[index] == '*' && script.getOrNull(index + 1) == '/')) index += 1
                    index = (index + 2).coerceAtMost(script.length)
                }

                char == '\'' || char == '"' || char == '`' -> {
                    val quote = char
                    index += 1
                    while (index < script.length) {
                        when {
                            script[index] == '\\' -> index += 2
                            script[index] == quote -> {
                                index += 1
                                break
                            }

                            else -> index += 1
                        }
                    }
                }

                char.isJavaScriptIdentifierStart() -> {
                    val start = index
                    index += 1
                    while (index < script.length && script[index].isJavaScriptIdentifierPart()) index += 1
                    yield(script.substring(start, index))
                }

                else -> index += 1
            }
        }
    }

    private fun Char.isJavaScriptIdentifierStart(): Boolean = this == '_' || this == '$' || isLetter()

    private fun Char.isJavaScriptIdentifierPart(): Boolean = isJavaScriptIdentifierStart() || isDigit()
}

private class ScriptValidationException(message: String) : IllegalArgumentException(message)

internal data class UnsupportedScriptFeature(val name: String, val error: String)
