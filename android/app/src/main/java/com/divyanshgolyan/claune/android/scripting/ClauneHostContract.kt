package com.divyanshgolyan.claune.android.scripting

internal object ClauneHostContract {
    val exposedMethodNames: List<String>
        get() = hostFunctions.map(HostFunction::name)

    val typeDefinitions: String
        get() = buildString {
            appendLine("export type WaitStateType = \"package\" | \"element\" | \"text\";")
            appendLine("export type WaitStateValue = string | RegExp;")
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
            appendLine("export interface ScreenWindow {")
            appendLine("  packageName: string;")
            appendLine("  className?: string | null;")
            appendLine("  type: string;")
            appendLine("  layer: number;")
            appendLine("  active: boolean;")
            appendLine("  focused: boolean;")
            appendLine("  bounds: Bounds;")
            appendLine("  visibleText: string[];")
            appendLine("  actionableElementCount: number;")
            appendLine("  selected: boolean;")
            appendLine("  selectionReason?: string | null;")
            appendLine("}")
            appendLine()
            appendLine("export interface VisibleElement {")
            appendLine("  id: string;")
            appendLine("  ref: string;")
            appendLine("  elementId: string;")
            appendLine("  normalizedLabel: string;")
            appendLine(
                "  textFields: { label?: string | null; text?: string | null; contentDescription?: string | null; resourceId?: string | null; className?: string | null };",
            )
            appendLine("  role: string;")
            appendLine("  className?: string | null;")
            appendLine("  resourceId?: string | null;")
            appendLine("  bounds: Bounds;")
            appendLine("  center: [number, number];")
            appendLine(
                "  state: { enabled: boolean; checked: boolean; selected: boolean; focused: boolean; editable: boolean; scrollable: boolean; clickable: boolean; focusable: boolean };",
            )
            appendLine(
                "  visibility: { a11yVisibleToUser: boolean; hasNonEmptyBounds: boolean; intersectsSelectedWindow: boolean; visibleAreaRatio: number; selectedWindow: boolean; occlusion: string; confidence: number };",
            )
            appendLine("  rawRefs: string[];")
            appendLine("  groupIds: string[];")
            appendLine("}")
            appendLine()
            appendLine("export interface VisibleGroup {")
            appendLine("  id: string;")
            appendLine("  role: string;")
            appendLine("  labelSummary: string;")
            appendLine("  bounds: Bounds;")
            appendLine("  elementIds: string[];")
            appendLine("  actionIds: string[];")
            appendLine("  parentGroupId?: string | null;")
            appendLine("  childGroupIds: string[];")
            appendLine("  confidence: number;")
            appendLine("  evidence: string[];")
            appendLine("}")
            appendLine()
            appendLine("export interface ActionAffordance {")
            appendLine("  id: string;")
            appendLine("  label: string;")
            appendLine("  kind: \"click\" | \"type\" | \"scroll\";")
            appendLine("  bounds: Bounds;")
            appendLine("  center: [number, number];")
            appendLine("  enabled: boolean;")
            appendLine("  targetRef: string;")
            appendLine("  targetElementId: string;")
            appendLine("  equivalentRefs: string[];")
            appendLine("  fallbackMethod: \"performAction\" | \"clickableParent\" | \"typeFocused\" | \"scroll\" | \"tapCenter\";")
            appendLine("  scope: { groupId?: string | null; elementId?: string | null };")
            appendLine("  confidence: number;")
            appendLine("  evidence: string[];")
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
            appendLine("  mode: \"interactions\" | \"diff\" | \"compact_snapshot\" | \"full_snapshot\";")
            appendLine("  reason: string;")
            appendLine("  baselineSnapshotId?: string | null;")
            appendLine("  currentSnapshotId: string;")
            appendLine("  snapshotId: string;")
            appendLine("  capturedAt?: string | null;")
            appendLine("  foregroundPackage: string;")
            appendLine("  selectedWindowReason?: string | null;")
            appendLine("  baselineMissing: boolean;")
            appendLine("  stats: ScreenDiffStats;")
            appendLine("  canonicalText?: string | null;")
            appendLine("  diff?: string | null;")
            appendLine("  windows: ScreenWindow[];")
            appendLine("  selectedWindow?: ScreenWindow | null;")
            appendLine("  summaryText?: string | null;")
            appendLine("  elements: VisibleElement[];")
            appendLine("  groups: VisibleGroup[];")
            appendLine("  actions: ActionAffordance[];")
            appendLine(
                "  diagnostics?: { visibleElementCount: number; actionCount: number; groupCount: number; rawVisibleNodeCount: number } | null;",
            )
            appendLine("}")
            appendLine()
            appendLine("export interface ScreenObserveOptions {")
            appendLine("  mode?: \"interactions\" | \"compact\" | \"full\";")
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
            appendLine("export interface TapTextOptions {")
            appendLine("  exact?: boolean;")
            appendLine("  first?: boolean;")
            appendLine("}")
            appendLine()
            appendLine("export interface ClauneHost {")
            hostFunctions.forEach { function ->
                append("  ")
                append(function.renderTypeSignature())
                appendLine()
            }
            appendLine(
                "  /** Find the first visible interaction element matching text, id, ref, role, or state criteria. */ findElement(screen: ScreenObservation, selector: InteractionSelector): VisibleElement | null;",
            )
            appendLine(
                "  /** Find the first visible group matching label text, id, role, or minimum confidence. */ findGroup(screen: ScreenObservation, selector: InteractionSelector): VisibleGroup | null;",
            )
            appendLine(
                "  /** Find the first action matching label, id, kind, enabled state, or group scope. */ findAction(screenOrGroup: ScreenObservation | VisibleGroup, selector: InteractionSelector): ActionAffordance | null;",
            )
            appendLine("}")
            appendLine()
            appendLine("export interface InteractionSelector {")
            appendLine("  id?: string;")
            appendLine("  ref?: string;")
            appendLine("  text?: string | RegExp;")
            appendLine("  label?: string | RegExp;")
            appendLine("  role?: string;")
            appendLine("  kind?: string;")
            appendLine("  enabled?: boolean;")
            appendLine("  editable?: boolean;")
            appendLine("  groupId?: string;")
            appendLine("  minConfidence?: number;")
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
            appendLine("function __clauneMatchesText(value, query) {")
            appendLine("  if (query == null) return true;")
            appendLine("  const text = String(value || \"\");")
            appendLine("  return query instanceof RegExp ? query.test(text) : text.toLowerCase().includes(String(query).toLowerCase());")
            appendLine("}")
            appendLine()
            appendLine("function __clauneTapTextExact(options) {")
            appendLine("  if (options && typeof options === \"object\") return Boolean(options.exact ?? true);")
            appendLine("  return Boolean(options ?? true);")
            appendLine("}")
            appendLine()
            appendLine("function __clauneTapTextFirst(options, first) {")
            appendLine("  if (first != null) return Boolean(first);")
            appendLine("  if (options && typeof options === \"object\") return Boolean(options.first ?? false);")
            appendLine("  return options === true;")
            appendLine("}")
            appendLine()
            appendLine("function __clauneMatchesInteraction(item, selector) {")
            appendLine("  const s = selector || {};")
            appendLine("  if (s.id != null && item.id !== s.id && item.elementId !== s.id) return false;")
            appendLine("  if (s.ref != null && item.ref !== s.ref && item.targetRef !== s.ref) return false;")
            appendLine("  if (s.role != null && item.role !== s.role) return false;")
            appendLine("  if (s.kind != null && item.kind !== s.kind) return false;")
            appendLine("  if (s.enabled != null && ((item.enabled ?? item.state?.enabled) !== s.enabled)) return false;")
            appendLine("  if (s.editable != null && item.state?.editable !== s.editable) return false;")
            appendLine(
                "  if (s.groupId != null && item.scope?.groupId !== s.groupId && !(item.groupIds || []).includes(s.groupId)) return false;",
            )
            appendLine(
                "  if (s.minConfidence != null && (item.confidence ?? item.visibility?.confidence ?? 0) < s.minConfidence) return false;",
            )
            appendLine("  const label = item.normalizedLabel || item.label || item.labelSummary || \"\";")
            appendLine("  if (s.text != null && !__clauneMatchesText(label, s.text)) return false;")
            appendLine("  if (s.label != null && !__clauneMatchesText(label, s.label)) return false;")
            appendLine("  return true;")
            appendLine("}")
            appendLine()
            appendLine("let __clauneLastScreen = null;")
            appendLine()
            appendLine("const __clauneNative = {")
            hostFunctions.forEachIndexed { index, function ->
                append(function.renderBootstrapFunction())
                if (index != hostFunctions.lastIndex) {
                    appendLine(",")
                } else {
                    appendLine()
                }
            }
            appendLine("};")
            appendLine()
            appendLine("globalThis.claune = Object.freeze(Object.assign({}, __clauneNative, {")
            appendLine("  observeScreen(options) {")
            appendLine("    const screen = __clauneNative.observeScreen(options);")
            appendLine("    __clauneLastScreen = screen;")
            appendLine("    return screen;")
            appendLine("  },")
            appendLine("  findElement(screen, selector) {")
            appendLine("    return ((screen && screen.elements) || []).find((item) => __clauneMatchesInteraction(item, selector)) || null;")
            appendLine("  },")
            appendLine("  findGroup(screen, selector) {")
            appendLine("    return ((screen && screen.groups) || []).find((item) => __clauneMatchesInteraction(item, selector)) || null;")
            appendLine("  },")
            appendLine("  findAction(screenOrGroup, selector) {")
            appendLine("    const s = selector || {};")
            appendLine("    const source = screenOrGroup || {};")
            appendLine("    const actions = source.actions || [];")
            appendLine("    const ids = source.actionIds;")
            appendLine("    return actions.find((item) => __clauneMatchesInteraction(item, s)) ||")
            appendLine(
                "      ((__clauneLastScreen && ids) ? __clauneLastScreen.actions.find((item) => ids.includes(item.id) && __clauneMatchesInteraction(item, s)) : null) || null;",
            )
            appendLine("  }")
            appendLine("}));")
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
            "The `claune` global exposes installed-app discovery, app launch, interaction-state observation, raw-node search, selector, action, focused input, tap, typing, scroll, navigation, and wait helpers."

    val promptSummary: String
        get() = buildString {
            appendLine("Claune JS is available through bash:")
            appendLine("  claune-js - <<'JS'      # run inline JavaScript from stdin")
            appendLine("  claune-js /work/scripts/task.js [args...]")
            appendLine("  claune-js --help [topic]")
            appendLine()
            appendLine("Top-level claune APIs:")
            hostFunctions.chunked(4).forEach { chunk ->
                append("  ")
                appendLine(chunk.joinToString(", ") { it.name })
            }
            appendLine()
            appendLine("Help topics: observe, actions, typing, navigation, raw, selectors, types, all, or any API name.")
        }.trim()

    fun cliHelp(topic: String? = null): String {
        val normalizedTopic = topic?.trim().orEmpty()
        if (normalizedTopic.isBlank()) return topLevelHelp()

        hostFunctions.firstOrNull { it.name.equals(normalizedTopic, ignoreCase = true) }?.let { function ->
            return buildString {
                appendLine("claune.${function.name}")
                appendLine(function.documentation)
                appendLine()
                appendLine(function.renderPlainSignature())
                appendLine("Returns: ${function.returnType}")
            }.trim()
        }

        return when (normalizedTopic.lowercase()) {
            "all" -> modelContractBlock
            "types" -> typeDefinitions
            "observe", "observation" -> topicHelp("observe", "Screen observation and inspection APIs.")
            "actions", "action", "tap" -> topicHelp("actions", "Tap, action, wait, and selector-driven interaction APIs.")
            "typing", "input" -> topicHelp("typing", "Focused-input and selector-driven typing APIs.")
            "navigation", "nav" -> topicHelp("navigation", "App launch, Back/Home, scrolling, and state-wait APIs.")
            "raw" -> topicHelp("raw", "Raw accessibility-tree fallback APIs.")
            "selectors", "selector" -> selectorHelp()
            "functions", "apis" -> functionListHelp()
            else -> buildString {
                appendLine("Unknown claune-js help topic: $topic")
                appendLine()
                append(topLevelHelp())
            }.trim()
        }
    }

    private val hostFunctions =
        listOf(
            HostFunction(
                name = "observeScreen",
                nativeBinding = "__clauneObserveScreenJson",
                returnType = "ScreenObservation",
                documentation = "Capture the latest screen interaction state. Use mode compact or full only for diagnostic summaries.",
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
                documentation =
                "Tap an actionable element whose visible text or label matches the given text. " +
                    "Use { first: true } only after inspecting duplicate matches.",
                parameters = listOf(
                    HostParameter("text", "string", "String(%s)"),
                    HostParameter(
                        "options",
                        "boolean | TapTextOptions",
                        "__clauneTapTextExact(%s)",
                        optional = true,
                    ),
                    HostParameter(
                        "first",
                        "boolean",
                        "__clauneTapTextFirst(options, %s)",
                        optional = true,
                    ),
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
                name = "performAction",
                nativeBinding = "__claunePerformActionJson",
                returnType = "HostSuccessOutcome",
                documentation = "Perform a deduplicated interaction action id from the latest screen interaction state.",
                parameters = listOf(HostParameter("actionId", "string", "String(%s)")),
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
                documentation = "Wait for a foreground package, element id, or visible text condition. Value may be a string or RegExp.",
                parameters = listOf(
                    HostParameter("type", "WaitStateType", "String(%s)"),
                    HostParameter("value", "WaitStateValue", "String(%s)"),
                    HostParameter("timeoutMs", "number", "Number(%s ?? 0)"),
                ),
            ),
        )

    private fun topLevelHelp(): String = buildString {
        appendLine("claune-js")
        appendLine("Run synchronous JavaScript against the Android phone-control host.")
        appendLine()
        appendLine("Usage:")
        appendLine("  claune-js - <<'JS'")
        appendLine("  const screen = claune.observeScreen();")
        appendLine("  return { foregroundPackage: screen.foregroundPackage };")
        appendLine("  JS")
        appendLine("  claune-js /work/scripts/task.js [args...]")
        appendLine("  claune-js --help [topic]")
        appendLine()
        appendLine("Globals:")
        appendLine("  claune   Phone observation/action host object.")
        appendLine("  argv     Script arguments after script path.")
        appendLine("  stdin    Text piped into the script for file-based scripts.")
        appendLine("  print(), console.log(), console.error()")
        appendLine()
        appendLine("Topics:")
        appendLine("  functions, observe, actions, typing, navigation, raw, selectors, types, all")
        appendLine()
        appendLine("Common APIs:")
        hostFunctions.chunked(4).forEach { chunk ->
            append("  ")
            appendLine(chunk.joinToString(", ") { it.name })
        }
    }.trim()

    private fun topicHelp(topic: String, description: String): String {
        val functions = hostFunctions.filter { it.helpTopic == topic }
        return buildString {
            appendLine(topic)
            appendLine(description)
            appendLine()
            functions.forEach { function ->
                appendLine(function.renderPlainSignature())
                appendLine("  ${function.documentation}")
            }
        }.trim()
    }

    private fun functionListHelp(): String = buildString {
        hostFunctions.forEach { function ->
            appendLine(function.renderPlainSignature())
            appendLine("  ${function.documentation}")
        }
    }.trim()

    private fun selectorHelp(): String = buildString {
        appendLine("selectors")
        appendLine(
            "Use selectors with tapSelector, focusSelector, typeIntoSelector, waitForSelector, " +
                "findElement, findGroup, and findAction.",
        )
        appendLine()
        appendLine("ElementSelector fields:")
        appendLine("  ref, label, text, textExact, contentDescription, resourceId, role")
        appendLine("  clickable, focusable, editable, focused, enabled, checked, selected, scrollable, first")
        appendLine()
        appendLine("InteractionSelector fields:")
        appendLine("  id, ref, text, label, role, kind, enabled, editable, groupId, minConfidence")
        appendLine()
        appendLine("Run `claune-js --help types` for full TypeScript definitions.")
    }.trim()
}

private data class HostParameter(
    val name: String,
    val typeSignature: String,
    val bootstrapExpression: String,
    val optional: Boolean = false,
) {
    fun renderTypeSignature(): String = "$name${if (optional) "?" else ""}: $typeSignature"

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
    val helpTopic: String
        get() = when (name) {
            "observeScreen", "diffScreen", "inspectScreen" -> "observe"
            "findRawNodes" -> "raw"
            "typeIntoSelector", "typeIntoFocused", "typeIntoElement", "focusSelector" -> "typing"
            "listInstalledApps",
            "launchApp",
            "scrollRef",
            "scrollScreen",
            "scrollContainer",
            "pressBack",
            "pressHome",
            "waitForState",
            -> "navigation"
            else -> "actions"
        }

    fun renderTypeSignature(): String {
        val args = parameters.joinToString(", ") { it.renderTypeSignature() }
        return "/** $documentation */ $name($args): $returnType;"
    }

    fun renderPlainSignature(): String {
        val args = parameters.joinToString(", ") { it.renderTypeSignature() }
        return "claune.$name($args): $returnType"
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
