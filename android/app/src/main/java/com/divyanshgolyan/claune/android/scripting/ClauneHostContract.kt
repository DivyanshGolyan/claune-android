package com.divyanshgolyan.claune.android.scripting

internal object ClauneHostContract {
    val exposedMethodNames: List<String>
        get() = hostFunctions.map(HostFunction::name)

    val typeDefinitions: String
        get() = buildString {
            appendLine("export type WaitStateType = \"package\" | \"element\" | \"text\";")
            appendLine("export type Bounds = [number, number, number, number];")
            appendLine()
            appendLine("export interface HostSuccessOutcome<TData = unknown> {")
            appendLine("  ok: true;")
            appendLine("  message: string;")
            appendLine("  data?: TData;")
            appendLine("}")
            appendLine()
            appendLine("export interface ScreenNode {")
            appendLine("  elementId: string;")
            appendLine("  ref: string;")
            appendLine("  role: string;")
            appendLine("  label: string;")
            appendLine("  text?: string | null;")
            appendLine("  contentDescription?: string | null;")
            appendLine("  resourceId?: string | null;")
            appendLine("  className?: string | null;")
            appendLine("  clickable: boolean;")
            appendLine("  focusable: boolean;")
            appendLine("  editable: boolean;")
            appendLine("  focused: boolean;")
            appendLine("  enabled: boolean;")
            appendLine("  checked: boolean;")
            appendLine("  selected: boolean;")
            appendLine("  scrollable: boolean;")
            appendLine("  bounds: Bounds;")
            appendLine("  center: [number, number];")
            appendLine("  actions: string[];")
            appendLine("  tapFallbackEligible: boolean;")
            appendLine("  clickabilityReason: string;")
            appendLine("  clickableParentDepth?: number | null;")
            appendLine("  clickableParentClassName?: string | null;")
            appendLine("  clickableDescendantPath?: string | null;")
            appendLine("  clickableDescendantClassName?: string | null;")
            appendLine("}")
            appendLine()
            appendLine("export interface ScreenDiffStats {")
            appendLine("  additions: number;")
            appendLine("  removals: number;")
            appendLine("  unchanged: number;")
            appendLine("  beforeLineCount: number;")
            appendLine("  afterLineCount: number;")
            appendLine("  changeRatio: number;")
            appendLine("}")
            appendLine()
            appendLine("export interface ScreenObservation {")
            appendLine("  mode: \"diff\" | \"compact_snapshot\" | \"full_snapshot\";")
            appendLine("  reason: string;")
            appendLine("  baselineSnapshotId?: string | null;")
            appendLine("  currentSnapshotId: string;")
            appendLine("  foregroundPackage: string;")
            appendLine("  selectedWindowReason?: string | null;")
            appendLine("  baselineMissing: boolean;")
            appendLine("  stats: ScreenDiffStats;")
            appendLine("  canonicalText?: string | null;")
            appendLine("  diff?: string | null;")
            appendLine("}")
            appendLine()
            appendLine("export interface ScreenObserveOptions {")
            appendLine("  mode?: \"compact\" | \"full\";")
            appendLine("  includeDiff?: boolean;")
            appendLine("}")
            appendLine()
            appendLine("export interface ScreenDiffOptions {")
            appendLine("  baselineSnapshotId?: string | null;")
            appendLine("}")
            appendLine()
            appendLine("export interface ScreenInspectOptions {")
            appendLine("  text?: string;")
            appendLine("  includeAll?: boolean;")
            appendLine("  limit?: number;")
            appendLine("}")
            appendLine()
            appendLine("export interface ScreenInspection {")
            appendLine("  snapshotId: string;")
            appendLine("  capturedAt: string;")
            appendLine("  foregroundPackage: string;")
            appendLine("  query?: string | null;")
            appendLine("  visibleElements: ScreenNode[];")
            appendLine("  actionableElements: ScreenNode[];")
            appendLine("  selectedWindowReason?: string | null;")
            appendLine("}")
            appendLine()
            appendLine(
                "export type RawNodeSearchField = \"label\" | \"text\" | \"contentDescription\" | " +
                    "\"resourceId\" | \"className\" | \"role\" | \"actions\";",
            )
            appendLine()
            appendLine("export interface RawNodeSearchOptions {")
            appendLine("  pattern: string;")
            appendLine("  flags?: string;")
            appendLine("  fields?: RawNodeSearchField[];")
            appendLine("  limit?: number;")
            appendLine("  includeContext?: boolean;")
            appendLine("}")
            appendLine()
            appendLine("export interface RawNodeMatch {")
            appendLine("  node: ScreenNode;")
            appendLine("  matchedFields: string[];")
            appendLine("  matchedText: string;")
            appendLine("  nearestActionable?: ScreenNode | null;")
            appendLine("  ancestorLabels: string[];")
            appendLine("  childLabels: string[];")
            appendLine("}")
            appendLine()
            appendLine("export interface RawNodeSearchResult {")
            appendLine("  snapshotId: string;")
            appendLine("  foregroundPackage: string;")
            appendLine("  pattern: string;")
            appendLine("  error?: string | null;")
            appendLine("  matches: RawNodeMatch[];")
            appendLine("}")
            appendLine()
            appendLine("export interface InstalledApp {")
            appendLine("  label: string;")
            appendLine("  packageName: string;")
            appendLine("  activityName?: string | null;")
            appendLine("}")
            appendLine()
            appendLine("export interface ElementSelector {")
            appendLine("  ref?: string;")
            appendLine("  label?: string;")
            appendLine("  text?: string;")
            appendLine("  textExact?: boolean;")
            appendLine("  contentDescription?: string;")
            appendLine("  resourceId?: string;")
            appendLine("  role?: string;")
            appendLine("  clickable?: boolean;")
            appendLine("  focusable?: boolean;")
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
            "The `claune` global exposes installed-app discovery, app launch, screen observation/diff, raw-node search, selector, focused input, tap, typing, scroll, navigation, and wait helpers."

    private val hostFunctions =
        listOf(
            HostFunction(
                name = "observeScreen",
                nativeBinding = "__clauneObserveScreenJson",
                returnType = "ScreenObservation",
                documentation = "Capture the latest screen observation. Returns compact canonical text or diff, " +
                    "depending on baseline availability.",
                parameters = listOf(HostParameter("options", "ScreenObserveOptions", "JSON.stringify(%s ?? {})")),
                throwsOnFailure = false,
            ),
            HostFunction(
                name = "diffScreen",
                nativeBinding = "__clauneDiffScreenJson",
                returnType = "ScreenObservation",
                documentation = "Capture the current screen and compare it with a prior baseline snapshot id, " +
                    "or the previous screen state in this run.",
                parameters = listOf(HostParameter("options", "ScreenDiffOptions", "JSON.stringify(%s ?? {})")),
                throwsOnFailure = false,
            ),
            HostFunction(
                name = "inspectScreen",
                nativeBinding = "__clauneInspectScreenJson",
                returnType = "ScreenInspection",
                documentation =
                "Inspect bounded visible elements, including non-actionable text, when a semantic tap fails or the UI looks visually tappable but not accessibility-clickable.",
                parameters = listOf(HostParameter("options", "ScreenInspectOptions", "JSON.stringify(%s ?? {})")),
                throwsOnFailure = false,
            ),
            HostFunction(
                name = "findRawNodes",
                nativeBinding = "__clauneFindRawNodesJson",
                returnType = "RawNodeSearchResult",
                documentation =
                "Search the latest raw accessibility tree for expected targets that the canonical screen summary did not surface. Returns bounded node matches with refs, bounds, and nearest actionable targets.",
                parameters = listOf(HostParameter("options", "RawNodeSearchOptions", "JSON.stringify(%s ?? {})")),
                throwsOnFailure = false,
            ),
            HostFunction(
                name = "listInstalledApps",
                nativeBinding = "__clauneListInstalledAppsJson",
                returnType = "InstalledApp[]",
                documentation = "List launchable installed apps with labels, package names, and launcher activity names.",
                parameters = emptyList(),
                throwsOnFailure = false,
            ),
            HostFunction(
                name = "launchApp",
                nativeBinding = "__clauneLaunchAppJson",
                returnType = "HostSuccessOutcome",
                documentation = "Launch an installed app by package name. Use listInstalledApps first when the package name is unknown.",
                parameters = listOf(HostParameter("packageName", "string", "String(%s)")),
            ),
            HostFunction(
                name = "tapRef",
                nativeBinding = "__clauneTapRefJson",
                returnType = "HostSuccessOutcome",
                documentation = "Tap an actionable element by snapshot ref.",
                parameters = listOf(HostParameter("ref", "string", "String(%s)")),
            ),
            HostFunction(
                name = "tapText",
                nativeBinding = "__clauneTapTextJson",
                returnType = "HostSuccessOutcome",
                documentation = "Tap the best actionable element whose visible text or label matches the given text.",
                parameters = listOf(
                    HostParameter("text", "string", "String(%s)"),
                    HostParameter("exact", "boolean", "Boolean(%s ?? true)"),
                ),
            ),
            HostFunction(
                name = "tapPoint",
                nativeBinding = "__clauneTapPointJson",
                returnType = "HostSuccessOutcome",
                documentation =
                "Tap absolute screen coordinates. Use only after semantic/ref taps fail " +
                    "and a fresh inspection proves the requested target is visually present.",
                parameters = listOf(
                    HostParameter("x", "number", "Number(%s)"),
                    HostParameter("y", "number", "Number(%s)"),
                ),
            ),
            HostFunction(
                name = "tapBounds",
                nativeBinding = "__clauneTapBoundsJson",
                returnType = "HostSuccessOutcome",
                documentation =
                "Tap the center of inspected bounds. Use only for verified visible non-actionable targets, " +
                    "then immediately re-observe.",
                parameters = listOf(HostParameter("bounds", "Bounds", "JSON.stringify(%s ?? [])")),
            ),
            HostFunction(
                name = "scrollRef",
                nativeBinding = "__clauneScrollRefJson",
                returnType = "HostSuccessOutcome",
                documentation = "Scroll a scrollable actionable element by snapshot ref in the given direction.",
                parameters = listOf(
                    HostParameter("ref", "string", "String(%s)"),
                    HostParameter("direction", "\"up\" | \"down\"", "String(%s)"),
                ),
            ),
            HostFunction(
                name = "scrollScreen",
                nativeBinding = "__clauneScrollScreenJson",
                returnType = "HostSuccessOutcome",
                documentation = "Scroll the best visible scrollable container on the current screen in the given direction.",
                parameters = listOf(
                    HostParameter("direction", "\"up\" | \"down\"", "String(%s)"),
                ),
            ),
            HostFunction(
                name = "focusSelector",
                nativeBinding = "__clauneFocusSelectorJson",
                returnType = "HostSuccessOutcome",
                documentation = "Activate a selector-matched control and wait for an editable field to become available for typing.",
                parameters = listOf(
                    HostParameter("selector", "ElementSelector", "JSON.stringify(%s ?? {})"),
                    HostParameter("timeoutMs", "number", "Number(%s ?? 0)"),
                ),
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
                documentation = "Type text into an editable element matched by selector, activating a wrapper control first when needed.",
                parameters = listOf(
                    HostParameter("selector", "ElementSelector", "JSON.stringify(%s ?? {})"),
                    HostParameter("text", "string", "String(%s)"),
                ),
            ),
            HostFunction(
                name = "typeIntoFocused",
                nativeBinding = "__clauneTypeIntoFocusedJson",
                returnType = "HostSuccessOutcome",
                documentation = "Type text into the currently focused editable element when focus is already where you need it.",
                parameters = listOf(HostParameter("text", "string", "String(%s)")),
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
                documentation =
                "Scroll a scrollable container element id in the given direction. " +
                    "Prefer scrollRef when you already have a fresh snapshot ref.",
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

    fun renderBootstrapArgument(): String = bootstrapExpression.replace("%s", name)
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
                append("JSON.parse($nativeBinding($callArgs));\n")
            }
            append("  }")
        }
    }
}
