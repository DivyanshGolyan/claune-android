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
            appendLine("  errorCode?: string | null;")
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
            appendLine("  fallbackMethod: \"accessibilityAction\" | \"clickableParent\" | \"focusedInput\" | \"scroll\" | \"tapCenter\";")
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
            appendLine("export type LocatorText = string | RegExp;")
            appendLine("export type LocatorWaitState = \"visible\" | \"hidden\";")
            appendLine("export interface LocatorOptions { timeoutMs?: number; force?: boolean; }")
            appendLine("export interface LocatorWaitOptions { timeoutMs?: number; state?: LocatorWaitState; }")
            appendLine("export interface LocatorFilterOptions { hasText?: LocatorText; visible?: boolean; }")
            appendLine("export interface LocatorPressOptions { timeoutMs?: number; }")
            appendLine("export interface LocatorDescribeOptions { limit?: number; }")
            appendLine(
                "export interface LocatorDescription { kind: string; count: number; truncated: boolean; " +
                    "candidates: unknown[]; foregroundPackage?: string; selectedWindowReason?: string; }",
            )
            appendLine("export interface Locator {")
            appendLine("  click(options?: LocatorOptions): HostSuccessOutcome;")
            appendLine("  tap(options?: LocatorOptions): HostSuccessOutcome;")
            appendLine("  fill(text: string, options?: LocatorOptions): HostSuccessOutcome;")
            appendLine("  waitFor(options?: LocatorWaitOptions): HostSuccessOutcome;")
            appendLine("  isVisible(): boolean;")
            appendLine("  isHidden(): boolean;")
            appendLine("  count(): number;")
            appendLine("  describe(options?: LocatorDescribeOptions): LocatorDescription;")
            appendLine("  first(): Locator;")
            appendLine("  nth(index: number): Locator;")
            appendLine("  filter(options: LocatorFilterOptions): Locator;")
            appendLine("  textContent(options?: { timeoutMs?: number }): string;")
            appendLine("  allTextContents(): string[];")
            appendLine("  press(key: \"Enter\", options?: LocatorPressOptions): HostSuccessOutcome;")
            appendLine("  getByText(text: LocatorText, options?: TextLocatorOptions): Locator;")
            appendLine("  getByLabel(text: LocatorText, options?: TextLocatorOptions): Locator;")
            appendLine("  getByRole(role: string, options?: RoleOptions): Locator;")
            appendLine("  getByTestId(testId: string): Locator;")
            appendLine("  getByPlaceholder(text: LocatorText, options?: TextLocatorOptions): Locator;")
            appendLine("}")
            appendLine()
            appendLine("export interface LocatorAssertions {")
            appendLine("  toBeVisible(options?: { timeoutMs?: number }): HostSuccessOutcome;")
            appendLine("  toBeHidden(options?: { timeoutMs?: number }): HostSuccessOutcome;")
            appendLine("  toHaveText(text: LocatorText, options?: { timeoutMs?: number }): HostSuccessOutcome;")
            appendLine("  toHaveCount(count: number, options?: { timeoutMs?: number }): HostSuccessOutcome;")
            appendLine("}")
            appendLine()
            appendLine("export interface RoleOptions { name?: LocatorText; exact?: boolean; }")
            appendLine("export interface TextLocatorOptions { exact?: boolean; }")
            appendLine()
            appendLine("export interface ClauneDebugHost {")
            hostFunctions.filterNot { it.helpTopic == "locator" }.forEach { function ->
                append("  ")
                append(function.renderTypeSignature())
                appendLine()
            }
            appendLine("}")
            appendLine()
            appendLine("export interface ClauneAppsHost {")
            appendLine("  /** List launchable installed apps with labels and package names. */ list(): InstalledApp[];")
            appendLine("  /** Launch an app by package name. */ launch(packageName: string): HostSuccessOutcome;")
            appendLine("}")
            appendLine()
            appendLine("export interface ClauneDeviceHost {")
            appendLine("  /** Press Android Back. */ back(): HostSuccessOutcome;")
            appendLine("  /** Press Android Home. */ home(): HostSuccessOutcome;")
            appendLine(
                "  /** Return compact foreground package, selected window, keyboard/system UI, and focused element state. */ " +
                    "current(): HostSuccessOutcome;",
            )
            appendLine(
                "  /** Wait for foreground package when recovering from uncertainty; apps.launch already verifies foreground. */ waitForPackage(packageName: string, options?: { timeoutMs?: number }): HostSuccessOutcome;",
            )
            appendLine("}")
            appendLine()
            appendLine("export interface ClauneHost {")
            appendLine(
                "  /** Locate visible text using Playwright-style normalized text matching. */ getByText(text: LocatorText, options?: TextLocatorOptions): Locator;",
            )
            appendLine(
                "  /** Locate controls by accessible label/content description. */ getByLabel(text: LocatorText, options?: TextLocatorOptions): Locator;",
            )
            appendLine(
                "  /** Locate by Android role semantics, optionally narrowed by accessible name. */ getByRole(role: string, options?: RoleOptions): Locator;",
            )
            appendLine("  /** Locate by Android resource id as the Playwright test id analogue. */ getByTestId(testId: string): Locator;")
            appendLine(
                "  /** Locate inputs by placeholder-like accessible text. */ getByPlaceholder(text: LocatorText, options?: TextLocatorOptions): Locator;",
            )
            appendLine(
                "  /** Construct a locator from a selector string or structured selector. Prefer getBy* helpers. */ locator(selector: string | object): Locator;",
            )
            appendLine("  /** Create retrying locator assertions. */ expect(locator: Locator): LocatorAssertions;")
            appendLine("  /** Supported app-level phone APIs. */ apps: ClauneAppsHost;")
            appendLine("  /** Supported device navigation APIs. */ device: ClauneDeviceHost;")
            appendLine("  /** Diagnostic Android-specific APIs outside the preferred Playwright subset. */ debug: ClauneDebugHost;")
            appendLine("}")
            appendLine()
            appendLine("declare const claune: ClauneHost;")
        }.trim()

    val bootstrapJavascript: String
        get() = buildString {
            appendLine("function __clauneRequireOutcome(callName, outcomeJson) {")
            appendLine("  const outcome = typeof outcomeJson === \"string\" ? JSON.parse(outcomeJson) : outcomeJson;")
            appendLine("  if (!outcome || outcome.ok !== true) {")
            appendLine("    const message = outcome && outcome.message ? outcome.message : `${'$'}{callName} failed.`;")
            appendLine("    const error = new Error(`${'$'}{callName}: ${'$'}{message}`);")
            appendLine("    error.callName = callName;")
            appendLine("    error.errorCode = outcome && outcome.errorCode ? outcome.errorCode : null;")
            appendLine("    error.data = outcome && outcome.data ? outcome.data : null;")
            appendLine("    throw error;")
            appendLine("  }")
            appendLine("  return outcome;")
            appendLine("}")
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
            appendLine("function __clauneLocatorTextSpec(kind, value, options) {")
            appendLine("  const spec = { kind };")
            appendLine("  if (value instanceof RegExp) { spec.pattern = value.source; spec.flags = value.flags || \"\"; }")
            appendLine("  else spec.text = String(value);")
            appendLine("  if (options && typeof options === \"object\") spec.exact = Boolean(options.exact ?? false);")
            appendLine("  return spec;")
            appendLine("}")
            appendLine()
            appendLine("function __clauneFilterSpec(options) {")
            appendLine("  const filter = {};")
            appendLine("  const source = options || {};")
            appendLine(
                "  if (source.hasText instanceof RegExp) { filter.hasPattern = source.hasText.source; filter.hasFlags = source.hasText.flags || \"\"; }",
            )
            appendLine("  else if (source.hasText != null) filter.hasText = String(source.hasText);")
            appendLine("  if (source.visible != null) filter.visible = Boolean(source.visible);")
            appendLine("  return filter;")
            appendLine("}")
            appendLine()
            appendLine("function __clauneScopedSpec(scope, child) {")
            appendLine("  return Object.assign({}, child, { scope });")
            appendLine("}")
            appendLine()
            appendLine("function __clauneRoleSpec(role, options) {")
            appendLine("  const spec = { kind: \"role\", role: String(role) };")
            appendLine("  const name = options && options.name;")
            appendLine("  if (name instanceof RegExp) { spec.pattern = name.source; spec.flags = name.flags || \"\"; }")
            appendLine("  else if (name != null) spec.name = String(name);")
            appendLine("  if (options && typeof options === \"object\") spec.exact = Boolean(options.exact ?? false);")
            appendLine("  return spec;")
            appendLine("}")
            appendLine()
            appendLine("function __clauneGenericLocatorSpec(selector) {")
            appendLine("  if (selector === \"*\") return { kind: \"all\" };")
            appendLine("  if (typeof selector === \"string\") return { kind: \"text\", text: selector };")
            appendLine("  if (!selector || typeof selector !== \"object\") return { kind: \"text\", text: String(selector ?? \"\") };")
            appendLine("  if (selector.kind) return Object.assign({}, selector);")
            appendLine("  if (selector.text != null) return __clauneLocatorTextSpec(\"text\", selector.text, selector);")
            appendLine("  if (selector.label != null) return __clauneLocatorTextSpec(\"label\", selector.label, selector);")
            appendLine(
                "  if (selector.placeholder != null) return __clauneLocatorTextSpec(\"placeholder\", selector.placeholder, selector);",
            )
            appendLine("  if (selector.role != null) return __clauneRoleSpec(selector.role, selector);")
            appendLine(
                "  if (selector.testId != null || selector.resourceId != null) return { kind: \"testId\", testId: String(selector.testId ?? selector.resourceId) };",
            )
            appendLine("  return { kind: \"text\", text: \"\" };")
            appendLine("}")
            appendLine()
            appendLine("function __clauneLocator(spec) {")
            appendLine("  const frozenSpec = Object.freeze(Object.assign({}, spec));")
            appendLine("  const locator = {")
            appendLine("    __clauneLocator: true,")
            appendLine("    __spec: frozenSpec,")
            appendLine(
                "    click(options) { return __clauneRequireOutcome(\"locator.click\", __clauneNative.locatorClick(frozenSpec, options || {})); },",
            )
            appendLine("    tap(options) { return this.click(options); },")
            appendLine(
                "    fill(text, options) { return __clauneRequireOutcome(\"locator.fill\", __clauneNative.locatorFill(frozenSpec, String(text), options || {})); },",
            )
            appendLine(
                "    waitFor(options) { return __clauneRequireOutcome(\"locator.waitFor\", __clauneNative.locatorWaitFor(frozenSpec, options || {})); },",
            )
            appendLine(
                "    count() { return Number((__clauneRequireOutcome(\"locator.count\", __clauneNative.locatorCount(frozenSpec)).data || {}).count || 0); },",
            )
            appendLine(
                "    describe(options) { return (__clauneRequireOutcome(\"locator.describe\", __clauneNative.locatorDescribe(frozenSpec, options || {})).data || {}); },",
            )
            appendLine(
                "    isVisible() { return Boolean((__clauneRequireOutcome(\"locator.isVisible\", __clauneNative.locatorIsVisible(frozenSpec)).data || {}).visible); },",
            )
            appendLine(
                "    isHidden() { return Boolean((__clauneRequireOutcome(\"locator.isHidden\", __clauneNative.locatorIsHidden(frozenSpec)).data || {}).hidden); },",
            )
            appendLine("    first() { return __clauneLocator(Object.assign({}, frozenSpec, { index: 0 })); },")
            appendLine("    nth(index) { return __clauneLocator(Object.assign({}, frozenSpec, { index: Number(index) })); },")
            appendLine(
                "    filter(options) { return __clauneLocator(Object.assign({}, frozenSpec, { filters: (frozenSpec.filters || []).concat([__clauneFilterSpec(options)]) })); },",
            )
            appendLine(
                "    textContent(options) { return String((__clauneRequireOutcome(\"locator.textContent\", __clauneNative.locatorTextContent(frozenSpec, options || {})).data || {}).text ?? \"\"); },",
            )
            appendLine(
                "    allTextContents() { return (__clauneRequireOutcome(\"locator.allTextContents\", __clauneNative.locatorAllTextContents(frozenSpec)).data || {}).texts || []; },",
            )
            appendLine(
                "    press(key, options) { return __clauneRequireOutcome(\"locator.press\", __clauneNative.locatorPress(frozenSpec, String(key), options || {})); },",
            )
            appendLine(
                "    getByText(text, options) { return __clauneLocator(__clauneScopedSpec(frozenSpec, __clauneLocatorTextSpec(\"text\", text, options))); },",
            )
            appendLine(
                "    getByLabel(text, options) { return __clauneLocator(__clauneScopedSpec(frozenSpec, __clauneLocatorTextSpec(\"label\", text, options))); },",
            )
            appendLine(
                "    getByRole(role, options) { return __clauneLocator(__clauneScopedSpec(frozenSpec, __clauneRoleSpec(role, options))); },",
            )
            appendLine(
                "    getByTestId(testId) { return __clauneLocator(__clauneScopedSpec(frozenSpec, { kind: \"testId\", testId: String(testId) })); },",
            )
            appendLine(
                "    getByPlaceholder(text, options) { return __clauneLocator(__clauneScopedSpec(frozenSpec, __clauneLocatorTextSpec(\"placeholder\", text, options))); }",
            )
            appendLine("  };")
            appendLine("  return Object.freeze(locator);")
            appendLine("}")
            appendLine()
            appendLine("function __clauneAssert(locator, matcher, payload) {")
            appendLine("  if (!locator || locator.__clauneLocator !== true) throw new Error(\"claune.expect() requires a Locator.\");")
            appendLine(
                "  return __clauneRequireOutcome(`expect.${'$'}{matcher}`, __clauneNative.locatorAssert(locator.__spec, Object.assign({ matcher }, payload || {})));",
            )
            appendLine("}")
            appendLine()
            appendLine("const __clauneDebug = {")
            appendLine("  observeScreen(options) {")
            appendLine("    const screen = __clauneNative.observeScreen(options);")
            appendLine("    return screen;")
            appendLine("  }${if (debugFunctions.isNotEmpty()) "," else ""}")
            debugFunctions.forEachIndexed { index, function ->
                append("  ${function.name}: __clauneNative.${function.name}")
                if (index != debugFunctions.lastIndex) {
                    appendLine(",")
                } else {
                    appendLine()
                }
            }
            appendLine("};")
            appendLine()
            appendLine("globalThis.claune = Object.freeze({")
            appendLine("  getByText(text, options) { return __clauneLocator(__clauneLocatorTextSpec(\"text\", text, options)); },")
            appendLine("  getByLabel(text, options) { return __clauneLocator(__clauneLocatorTextSpec(\"label\", text, options)); },")
            appendLine("  getByRole(role, options) { return __clauneLocator(__clauneRoleSpec(role, options)); },")
            appendLine("  getByTestId(testId) { return __clauneLocator({ kind: \"testId\", testId: String(testId) }); },")
            appendLine(
                "  getByPlaceholder(text, options) { return __clauneLocator(__clauneLocatorTextSpec(\"placeholder\", text, options)); },",
            )
            appendLine("  locator(selector) { return __clauneLocator(__clauneGenericLocatorSpec(selector)); },")
            appendLine("  expect(locator) {")
            appendLine("    return Object.freeze({")
            appendLine(
                "      toBeVisible(options) { return __clauneAssert(locator, \"toBeVisible\", { timeoutMs: Number((options || {}).timeoutMs ?? 5000) }); },",
            )
            appendLine(
                "      toBeHidden(options) { return __clauneAssert(locator, \"toBeHidden\", { timeoutMs: Number((options || {}).timeoutMs ?? 5000) }); },",
            )
            appendLine("      toHaveText(text, options) {")
            appendLine("        const payload = { timeoutMs: Number((options || {}).timeoutMs ?? 5000) };")
            appendLine(
                "        if (text instanceof RegExp) { payload.expectedPattern = text.source; payload.expectedFlags = text.flags || \"\"; } else payload.expectedText = String(text);",
            )
            appendLine("        return __clauneAssert(locator, \"toHaveText\", payload);")
            appendLine("      },")
            appendLine(
                "      toHaveCount(count, options) { return __clauneAssert(locator, \"toHaveCount\", { expectedCount: Number(count), timeoutMs: Number((options || {}).timeoutMs ?? 5000) }); }",
            )
            appendLine("    });")
            appendLine("  },")
            appendLine("  apps: Object.freeze({")
            appendLine("    list() { return __clauneNative.listInstalledApps(); },")
            appendLine("    launch(packageName) { return __clauneNative.launchApp(String(packageName)); }")
            appendLine("  }),")
            appendLine("  device: Object.freeze({")
            appendLine("    back() { return __clauneNative.pressBack(); },")
            appendLine("    home() { return __clauneNative.pressHome(); },")
            appendLine("    current() { return __clauneNative.deviceCurrent(); },")
            appendLine(
                "    waitForPackage(packageName, options) { return __clauneNative.waitForState(\"package\", String(packageName), Number((options || {}).timeoutMs ?? 5000)); }",
            )
            appendLine("  }),")
            appendLine("  debug: Object.freeze(__clauneDebug)")
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
            "The `claune` global exposes a synchronous Playwright-style locator/assertion subset plus Android diagnostics under `claune.debug`."

    val promptSummary: String
        get() = buildString {
            appendLine("Claune JS is available through bash:")
            appendLine("  claune-js - <<'JS'      # run inline JavaScript from stdin")
            appendLine("  claune-js /work/scripts/task.js [args...]")
            appendLine("  claune-js --help [topic]")
            appendLine()
            appendLine("Preferred top-level claune APIs:")
            appendLine("  ${preferredApiNames.joinToString(", ")}")
            appendLine("Supported phone namespaces:")
            appendLine("  apps.list, apps.launch, device.current, device.back, device.home, device.waitForPackage")
            appendLine("  apps.launch is idempotent and verifies foreground state; do not immediately pair it with waitForPackage.")
            appendLine("Diagnostics exist only for API-gap debugging: claune.debug.*")
            appendLine()
            appendLine("Help topics: locators, diagnostics, types, all.")
        }.trim()

    fun cliHelp(topic: String? = null): String {
        val normalizedTopic = topic?.trim().orEmpty()
        if (normalizedTopic.isBlank()) return topLevelHelp()

        debugFunctions.firstOrNull { it.name.equals(normalizedTopic, ignoreCase = true) }?.let { function ->
            return buildString {
                appendLine("claune.debug.${function.name}")
                appendLine(function.documentation)
                appendLine()
                appendLine(function.renderPlainSignature(receiver = "claune.debug"))
                appendLine("Returns: ${function.returnType}")
            }.trim()
        }

        return when (normalizedTopic.lowercase()) {
            "all" -> modelContractBlock
            "types" -> typeDefinitions
            "locators", "locator", "actions", "typing", "assertions", "expect" -> locatorHelp()
            "diagnostics", "debug", "observe", "observation", "navigation", "raw" -> diagnosticsHelp()
            "selectors", "selector" -> locatorHelp()
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
                name = "locatorQuery",
                nativeBinding = "__clauneLocatorQueryJson",
                returnType = "HostSuccessOutcome",
                documentation = "Internal locator query implementation for Playwright-subset locator objects.",
                parameters = listOf(HostParameter("spec", "object", "JSON.stringify(%s ?? {})")),
                throwsOnFailure = false,
            ),
            HostFunction(
                name = "locatorCount",
                nativeBinding = "__clauneLocatorCountJson",
                returnType = "HostSuccessOutcome",
                documentation = "Internal locator count implementation without candidate serialization.",
                parameters = listOf(HostParameter("spec", "object", "JSON.stringify(%s ?? {})")),
                throwsOnFailure = false,
            ),
            HostFunction(
                name = "locatorDescribe",
                nativeBinding = "__clauneLocatorDescribeJson",
                returnType = "HostSuccessOutcome",
                documentation = "Internal supported locator discovery implementation with compact candidate descriptions.",
                parameters = listOf(
                    HostParameter("spec", "object", "JSON.stringify(%s ?? {})"),
                    HostParameter("options", "LocatorDescribeOptions", "JSON.stringify(%s ?? {})"),
                ),
                throwsOnFailure = false,
            ),
            HostFunction(
                name = "locatorIsVisible",
                nativeBinding = "__clauneLocatorIsVisibleJson",
                returnType = "HostSuccessOutcome",
                documentation = "Internal locator visibility boolean implementation.",
                parameters = listOf(HostParameter("spec", "object", "JSON.stringify(%s ?? {})")),
                throwsOnFailure = false,
            ),
            HostFunction(
                name = "locatorIsHidden",
                nativeBinding = "__clauneLocatorIsHiddenJson",
                returnType = "HostSuccessOutcome",
                documentation = "Internal locator hidden-state boolean implementation.",
                parameters = listOf(HostParameter("spec", "object", "JSON.stringify(%s ?? {})")),
                throwsOnFailure = false,
            ),
            HostFunction(
                name = "locatorClick",
                nativeBinding = "__clauneLocatorClickJson",
                returnType = "HostSuccessOutcome",
                documentation = "Internal locator click/tap implementation with strictness and actionability checks.",
                parameters = listOf(
                    HostParameter("spec", "object", "JSON.stringify(%s ?? {})"),
                    HostParameter("options", "LocatorOptions", "JSON.stringify(%s ?? {})"),
                ),
                throwsOnFailure = false,
            ),
            HostFunction(
                name = "locatorFill",
                nativeBinding = "__clauneLocatorFillJson",
                returnType = "HostSuccessOutcome",
                documentation = "Internal locator fill implementation with editable-target activation.",
                parameters = listOf(
                    HostParameter("spec", "object", "JSON.stringify(%s ?? {})"),
                    HostParameter("text", "string", "String(%s)"),
                    HostParameter("options", "LocatorOptions", "JSON.stringify(%s ?? {})"),
                ),
                throwsOnFailure = false,
            ),
            HostFunction(
                name = "locatorWaitFor",
                nativeBinding = "__clauneLocatorWaitForJson",
                returnType = "HostSuccessOutcome",
                documentation = "Internal locator wait implementation.",
                parameters = listOf(
                    HostParameter("spec", "object", "JSON.stringify(%s ?? {})"),
                    HostParameter("options", "LocatorWaitOptions", "JSON.stringify(%s ?? {})"),
                ),
                throwsOnFailure = false,
            ),
            HostFunction(
                name = "locatorAssert",
                nativeBinding = "__clauneLocatorAssertJson",
                returnType = "HostSuccessOutcome",
                documentation = "Internal retrying locator assertion implementation.",
                parameters = listOf(
                    HostParameter("spec", "object", "JSON.stringify(%s ?? {})"),
                    HostParameter("assertion", "object", "JSON.stringify(%s ?? {})"),
                ),
                throwsOnFailure = false,
            ),
            HostFunction(
                name = "locatorTextContent",
                nativeBinding = "__clauneLocatorTextContentJson",
                returnType = "HostSuccessOutcome",
                documentation = "Internal strict locator text extraction.",
                parameters = listOf(
                    HostParameter("spec", "object", "JSON.stringify(%s ?? {})"),
                    HostParameter("options", "{ timeoutMs?: number }", "JSON.stringify(%s ?? {})"),
                ),
                throwsOnFailure = false,
            ),
            HostFunction(
                name = "locatorAllTextContents",
                nativeBinding = "__clauneLocatorAllTextContentsJson",
                returnType = "HostSuccessOutcome",
                documentation = "Internal multi-target locator text extraction.",
                parameters = listOf(HostParameter("spec", "object", "JSON.stringify(%s ?? {})")),
                throwsOnFailure = false,
            ),
            HostFunction(
                name = "locatorPress",
                nativeBinding = "__clauneLocatorPressJson",
                returnType = "HostSuccessOutcome",
                documentation = "Internal locator key press implementation.",
                parameters = listOf(
                    HostParameter("spec", "object", "JSON.stringify(%s ?? {})"),
                    HostParameter("key", "string", "String(%s)"),
                    HostParameter("options", "LocatorPressOptions", "JSON.stringify(%s ?? {})"),
                ),
                throwsOnFailure = false,
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
            HostFunction(
                name = "deviceCurrent",
                nativeBinding = "__clauneDeviceCurrentJson",
                returnType = "HostSuccessOutcome",
                documentation = "Capture compact current foreground package, selected window, keyboard/system UI, and focus state.",
                parameters = emptyList(),
                throwsOnFailure = false,
            ),
        )

    private val debugFunctions: List<HostFunction>
        get() = hostFunctions.filterNot { it.name == "observeScreen" || it.helpTopic == "locator" }

    private val preferredApiNames: List<String> =
        listOf("getByText", "getByLabel", "getByRole", "getByTestId", "getByPlaceholder", "locator", "expect", "apps", "device")

    private fun topLevelHelp(): String = buildString {
        appendLine("claune-js")
        appendLine("Run synchronous JavaScript against the Android phone-control host.")
        appendLine()
        appendLine("Usage:")
        appendLine("  claune-js - <<'JS'")
        appendLine("  claune.getByLabel(\"Search\").fill(\"milk\");")
        appendLine("  claune.getByPlaceholder(\"Search\").press(\"Enter\");")
        appendLine("  claune.expect(claune.getByText(\"milk\")).toBeVisible();")
        appendLine("  const visible = claune.locator(\"*\").describe({ limit: 20 });")
        appendLine("  return { stage: \"verified\" };")
        appendLine("  JS")
        appendLine("  claune-js /work/scripts/task.js [args...]")
        appendLine("  claune-js --help [topic]")
        appendLine()
        appendLine("Globals:")
        appendLine("  claune   Synchronous Playwright-style Android automation host.")
        appendLine("  argv     Script arguments after script path.")
        appendLine("  stdin    Text piped into the script for file-based scripts.")
        appendLine("  print(), console.log(), console.error()")
        appendLine()
        appendLine("Topics:")
        appendLine("  locators, diagnostics, types, all")
        appendLine()
        appendLine("Preferred APIs:")
        appendLine("  getByText, getByLabel, getByRole, getByTestId, getByPlaceholder, locator, expect, apps, device")
    }.trim()

    private fun locatorHelp(): String = buildString {
        appendLine("locators")
        appendLine("Preferred synchronous Playwright subset for Android accessibility automation.")
        appendLine()
        appendLine("claune.getByText(text, options?)")
        appendLine("claune.getByLabel(text, options?)")
        appendLine("claune.getByRole(role, { name?, exact? })")
        appendLine("claune.getByTestId(testId)")
        appendLine("claune.getByPlaceholder(text, options?)")
        appendLine("claune.locator(selector)")
        appendLine()
        appendLine("locator.click(options?), locator.tap(options?), locator.fill(text, options?)")
        appendLine("locator.waitFor({ state?: \"visible\" | \"hidden\", timeoutMs? })")
        appendLine("locator.isVisible(), locator.isHidden(), locator.describe({ limit? })")
        appendLine("locator.press(\"Enter\", options?), locator.textContent(options?), locator.allTextContents()")
        appendLine("Prefer locator.allTextContents() over loops of locator.nth(i).textContent().")
        appendLine("locator.filter({ hasText?, visible? }), locator.count(), locator.first(), locator.nth(index)")
        appendLine("claune.locator(\"*\").describe({ limit: 20 }) for supported broad discovery")
        appendLine("locator.getByText(...), getByLabel(...), getByRole(...), getByTestId(...), getByPlaceholder(...)")
        appendLine()
        appendLine("claune.expect(locator).toBeVisible(options?)")
        appendLine("claune.expect(locator).toBeHidden(options?)")
        appendLine("claune.expect(locator).toHaveText(textOrRegex, options?)")
        appendLine("claune.expect(locator).toHaveCount(count, options?)")
    }.trim()

    private fun diagnosticsHelp(): String = buildString {
        appendLine("diagnostics")
        appendLine("Android-specific escape hatches live under claune.debug and are not the preferred path.")
        appendLine()
        hostFunctions.filterNot { it.helpTopic == "locator" }.forEach { function ->
            appendLine(
                "claune.debug.${function.name}${function.parameters.joinToString(prefix = "(", postfix = ")") {
                    it.renderTypeSignature()
                }}: ${function.returnType}",
            )
            appendLine("  ${function.documentation}")
        }
    }.trim()

    private fun functionListHelp(): String = buildString {
        appendLine("Preferred APIs:")
        appendLine("claune.getByText(text, options?): Locator")
        appendLine("claune.getByLabel(text, options?): Locator")
        appendLine("claune.getByRole(role, options?): Locator")
        appendLine("claune.getByTestId(testId): Locator")
        appendLine("claune.getByPlaceholder(text, options?): Locator")
        appendLine("claune.locator(selector): Locator")
        appendLine("claune.expect(locator): LocatorAssertions")
        appendLine("claune.apps.list(): InstalledApp[]")
        appendLine("claune.apps.launch(packageName): HostSuccessOutcome")
        appendLine("  Idempotently ensures the package is foreground; returns immediately if it already is.")
        appendLine("claune.device.back(): HostSuccessOutcome")
        appendLine("claune.device.home(): HostSuccessOutcome")
        appendLine("claune.device.current(): HostSuccessOutcome")
        appendLine("claune.device.waitForPackage(packageName, options?): HostSuccessOutcome")
        appendLine("  Use only when recovering from uncertainty; launch already waits for foreground.")
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
            "locatorQuery",
            "locatorCount",
            "locatorDescribe",
            "locatorIsVisible",
            "locatorIsHidden",
            "locatorClick",
            "locatorFill",
            "locatorWaitFor",
            "locatorAssert",
            "locatorTextContent",
            "locatorAllTextContents",
            "locatorPress",
            -> "locator"
            "observeScreen", "diffScreen", "inspectScreen" -> "observe"
            "findRawNodes" -> "raw"
            "listInstalledApps",
            "launchApp",
            "scrollRef",
            "scrollScreen",
            "pressBack",
            "pressHome",
            "waitForState",
            "deviceCurrent",
            -> "navigation"
            else -> "actions"
        }

    fun renderTypeSignature(): String {
        val args = parameters.joinToString(", ") { it.renderTypeSignature() }
        return "/** $documentation */ $name($args): $returnType;"
    }

    fun renderPlainSignature(receiver: String = "claune"): String {
        val args = parameters.joinToString(", ") { it.renderTypeSignature() }
        return "$receiver.$name($args): $returnType"
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
