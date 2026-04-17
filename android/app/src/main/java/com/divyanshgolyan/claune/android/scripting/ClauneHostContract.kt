package com.divyanshgolyan.claune.android.scripting

internal object ClauneHostContract {
    val exposedMethodNames: List<String>
        get() = hostFunctions.map(HostFunction::name)

    val typeDefinitions: String
        get() = buildString {
            appendLine("export type WaitStateType = \"package\" | \"element\" | \"text\";")
            appendLine()
            appendLine("export interface HostSuccessOutcome<TData = unknown> {")
            appendLine("  ok: true;")
            appendLine("  message: string;")
            appendLine("  data?: TData;")
            appendLine("}")
            appendLine()
            appendLine("export interface UiElement {")
            appendLine("  id: string;")
            appendLine("  ref: string;")
            appendLine("  role: string;")
            appendLine("  label: string;")
            appendLine("  text?: string | null;")
            appendLine("  contentDescription?: string | null;")
            appendLine("  resourceId?: string | null;")
            appendLine("  className?: string | null;")
            appendLine("  clickable: boolean;")
            appendLine("  editable: boolean;")
            appendLine("  focused: boolean;")
            appendLine("  enabled: boolean;")
            appendLine("  checked: boolean;")
            appendLine("  selected: boolean;")
            appendLine("  scrollable: boolean;")
            appendLine("  bounds: [number, number, number, number];")
            appendLine("}")
            appendLine()
            appendLine("export interface UiSnapshot {")
            appendLine("  snapshotId: string;")
            appendLine("  capturedAt: string;")
            appendLine("  foregroundPackage: string;")
            appendLine("  visibleText: string[];")
            appendLine("  actionableElements: UiElement[];")
            appendLine("  focusedElementId?: string | null;")
            appendLine("}")
            appendLine()
            appendLine("export interface ElementSelector {")
            appendLine("  ref?: string;")
            appendLine("  text?: string;")
            appendLine("  textExact?: boolean;")
            appendLine("  contentDescription?: string;")
            appendLine("  resourceId?: string;")
            appendLine("  role?: string;")
            appendLine("  clickable?: boolean;")
            appendLine("  editable?: boolean;")
            appendLine("  focused?: boolean;")
            appendLine("  enabled?: boolean;")
            appendLine("  checked?: boolean;")
            appendLine("  selected?: boolean;")
            appendLine("  scrollable?: boolean;")
            appendLine("  first?: boolean;")
            appendLine("}")
            appendLine()
            appendLine("export interface ClauneHost {")
            hostFunctions.forEach { function ->
                append("  ")
                append(function.renderTypeSignature())
                appendLine()
            }
            appendLine("}")
            appendLine()
            appendLine("declare const claune: ClauneHost;")
        }.trim()

    val bootstrapJavascript: String
        get() = buildString {
            appendLine("function __clauneRequireOutcome(callName, outcomeJson) {")
            appendLine("  const outcome = JSON.parse(outcomeJson);")
            appendLine("  if (!outcome || outcome.ok !== true) {")
            appendLine("    const message = outcome && outcome.message ? outcome.message : `${'$'}{callName} failed.`;")
            appendLine("    throw new Error(`${'$'}{callName}: ${'$'}{message}`);")
            appendLine("  }")
            appendLine("  return outcome;")
            appendLine("}")
            appendLine()
            appendLine("globalThis.claune = Object.freeze({")
            hostFunctions.forEachIndexed { index, function ->
                append(function.renderBootstrapFunction())
                if (index != hostFunctions.lastIndex) {
                    appendLine(",")
                } else {
                    appendLine()
                }
            }
            appendLine("});")
        }.trim()

    val modelContractBlock: String
        get() = buildString {
            appendLine("TypeScript contract for the global `claune` object:")
            appendLine("```ts")
            appendLine(typeDefinitions)
            appendLine("```")
        }.trim()

    val scriptLabSummary: String =
        "Run JS directly against the embedded runtime using the generated Claune host contract. " +
            "The `claune` global exposes snapshot, selector, tap, typing, scroll, navigation, and wait helpers."

    private val hostFunctions =
        listOf(
            HostFunction(
                name = "observePhone",
                nativeBinding = "__clauneObservePhoneJson",
                returnType = "UiSnapshot",
                documentation = "Capture the latest phone snapshot. Re-observe after every UI-changing action before reusing refs or ids.",
                parameters = emptyList(),
                throwsOnFailure = false,
            ),
            HostFunction(
                name = "tapRef",
                nativeBinding = "__clauneTapRefJson",
                returnType = "HostSuccessOutcome",
                documentation = "Tap an actionable element by snapshot ref.",
                parameters = listOf(HostParameter("ref", "string", "String(%s)")),
            ),
            HostFunction(
                name = "tapSelector",
                nativeBinding = "__clauneTapSelectorJson",
                returnType = "HostSuccessOutcome",
                documentation = "Tap the best actionable element matching a selector.",
                parameters = listOf(HostParameter("selector", "ElementSelector", "JSON.stringify(%s ?? {})")),
            ),
            HostFunction(
                name = "tapElement",
                nativeBinding = "__clauneTapElementJson",
                returnType = "HostSuccessOutcome",
                documentation = "Tap an actionable element by element id from the latest snapshot.",
                parameters = listOf(HostParameter("elementId", "string", "String(%s)")),
            ),
            HostFunction(
                name = "typeIntoSelector",
                nativeBinding = "__clauneTypeIntoSelectorJson",
                returnType = "HostSuccessOutcome",
                documentation = "Type text into a selector-matched editable element.",
                parameters = listOf(
                    HostParameter("selector", "ElementSelector", "JSON.stringify(%s ?? {})"),
                    HostParameter("text", "string", "String(%s)"),
                ),
            ),
            HostFunction(
                name = "typeIntoElement",
                nativeBinding = "__clauneTypeIntoElementJson",
                returnType = "HostSuccessOutcome",
                documentation = "Type text into an editable element id from the latest snapshot.",
                parameters = listOf(
                    HostParameter("elementId", "string", "String(%s)"),
                    HostParameter("text", "string", "String(%s)"),
                ),
            ),
            HostFunction(
                name = "scrollContainer",
                nativeBinding = "__clauneScrollContainerJson",
                returnType = "HostSuccessOutcome",
                documentation = "Scroll a scrollable container element id in the given direction.",
                parameters = listOf(
                    HostParameter("elementId", "string", "String(%s)"),
                    HostParameter("direction", "\"up\" | \"down\"", "String(%s)"),
                ),
            ),
            HostFunction(
                name = "waitForSelector",
                nativeBinding = "__clauneWaitForSelectorJson",
                returnType = "HostSuccessOutcome",
                documentation = "Wait for a selector to match a distinctive UI element after navigation or mutation.",
                parameters = listOf(
                    HostParameter("selector", "ElementSelector", "JSON.stringify(%s ?? {})"),
                    HostParameter("timeoutMs", "number", "Number(%s ?? 0)"),
                ),
            ),
            HostFunction(
                name = "pressBack",
                nativeBinding = "__claunePressBackJson",
                returnType = "HostSuccessOutcome",
                documentation = "Press the system Back action.",
                parameters = emptyList(),
            ),
            HostFunction(
                name = "pressHome",
                nativeBinding = "__claunePressHomeJson",
                returnType = "HostSuccessOutcome",
                documentation = "Press the system Home action.",
                parameters = emptyList(),
            ),
            HostFunction(
                name = "waitForState",
                nativeBinding = "__clauneWaitForStateJson",
                returnType = "HostSuccessOutcome",
                documentation = "Wait for a foreground package, element id, or visible text condition.",
                parameters = listOf(
                    HostParameter("type", "WaitStateType", "String(%s)"),
                    HostParameter("value", "string", "String(%s)"),
                    HostParameter("timeoutMs", "number", "Number(%s ?? 0)"),
                ),
            ),
        )
}

private data class HostParameter(val name: String, val typeSignature: String, val bootstrapExpression: String) {
    fun renderTypeSignature(): String = "$name: $typeSignature"

    fun renderBootstrapArgument(): String = bootstrapExpression.format(name)
}

private data class HostFunction(
    val name: String,
    val nativeBinding: String,
    val returnType: String,
    val documentation: String,
    val parameters: List<HostParameter>,
    val throwsOnFailure: Boolean = true,
) {
    fun renderTypeSignature(): String {
        val args = parameters.joinToString(", ") { it.renderTypeSignature() }
        return "/** $documentation */ $name($args): $returnType;"
    }

    fun renderBootstrapFunction(): String {
        val params = parameters.joinToString(", ") { it.name }
        val callArgs = parameters.joinToString(", ") { it.renderBootstrapArgument() }
        return buildString {
            append("  $name($params) {\n")
            append("    return ")
            if (throwsOnFailure) {
                append("__clauneRequireOutcome(\"$name\", $nativeBinding($callArgs));\n")
            } else {
                append("JSON.parse($nativeBinding($callArgs)));\n")
            }
            append("  }")
        }
    }
}
