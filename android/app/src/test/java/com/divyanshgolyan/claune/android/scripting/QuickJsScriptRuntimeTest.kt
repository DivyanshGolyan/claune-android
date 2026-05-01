package com.divyanshgolyan.claune.android.scripting

import com.divyanshgolyan.claune.android.data.local.CodingSessionStore
import com.divyanshgolyan.claune.android.data.local.InMemorySessionLogStore
import com.divyanshgolyan.claune.android.runtime.ActionResult
import com.divyanshgolyan.claune.android.runtime.ElementRef
import com.divyanshgolyan.claune.android.runtime.PhoneActuator
import com.divyanshgolyan.claune.android.runtime.PhoneObserver
import com.divyanshgolyan.claune.android.runtime.ScreenNode
import com.divyanshgolyan.claune.android.runtime.ScreenState
import com.divyanshgolyan.claune.android.runtime.ScrollDirection
import com.divyanshgolyan.claune.android.runtime.SessionCoordinator
import com.divyanshgolyan.claune.android.shell.ClauneJsResult
import java.nio.file.Files
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeNoException
import org.junit.Test

class QuickJsScriptRuntimeTest {
    @Test
    fun `script source validator allows console usage`() {
        val unsupported =
            ScriptSourceValidator.firstUnsupportedFeature(
                """
                const locator = claune.getByText("Settings");
                console.log(locator.count());
                return {};
                """.trimIndent(),
            )

        assertNull(unsupported)
    }

    @Test
    fun `script source validator rejects Promise usage through the AST`() {
        assertEquals(
            "promise",
            ScriptSourceValidator.firstUnsupportedFeature("return Promise.resolve({ ok: true });")?.name,
        )
    }

    @Test
    fun `script source validator rejects async await and module syntax outside comments and strings`() {
        assertEquals(
            "async",
            ScriptSourceValidator.firstUnsupportedFeature("async function main() { return {}; }")?.name,
        )
        assertEquals(
            "await",
            ScriptSourceValidator.firstUnsupportedFeature("const value = await read(); return value;")?.name,
        )
        assertEquals(
            "module",
            ScriptSourceValidator.firstUnsupportedFeature("import value from './value.js'; return value;")?.name,
        )
        assertNull(
            ScriptSourceValidator.firstUnsupportedFeature(
                """
                // async await import export Promise
                return "async await import export Promise";
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun `script source validator ignores console text in comments and strings`() {
        val commentOnly =
            ScriptSourceValidator.firstUnsupportedFeature(
                """
                // Claune console notes belong in the summary, not the runtime.
                return { note: "console.log is just text here" };
                """.trimIndent(),
            )
        val stringOnly =
            ScriptSourceValidator.firstUnsupportedFeature(
                """
                return { note: "Promise.resolve is not actually being called" };
                """.trimIndent(),
            )

        assertNull(commentOnly)
        assertNull(stringOnly)
    }

    @Test
    fun `script source validator allows valid synchronous claune scripts`() {
        val unsupported =
            ScriptSourceValidator.firstUnsupportedFeature(
                """
                const target = claune.getByText("Settings");
                return { count: target.count() };
                """.trimIndent(),
            )

        assertNull(unsupported)
    }

    @Test
    fun `quickjs cli contract exposes argv stdin output functions and return printing`() {
        val globals = QuickJsCliContract.globalsBootstrapJavascript(listOf("first", "two"), "typed input")
        val output = QuickJsCliContract.outputBootstrapJavascript
        val wrapped = QuickJsCliContract.wrapScript("return { ok: true };")

        assertTrue(globals.contains("""globalThis.argv = ["first","two"];"""))
        assertTrue(globals.contains("""globalThis.stdin = "typed input";"""))
        assertTrue(output.contains("globalThis.print = function()"))
        assertTrue(output.contains("globalThis.console = Object.freeze"))
        assertTrue(output.contains("log: function()"))
        assertTrue(output.contains("error: function()"))
        assertTrue(output.contains("""if (typeof value === "string") return value;"""))
        assertTrue(output.contains("JSON.stringify(value)"))
        assertTrue(wrapped.contains("const __clauneCliResult"))
        assertTrue(wrapped.contains("__clauneCliResult !== undefined"))
        assertTrue(wrapped.contains("__clauneWriteCliReturn(__clauneCliResult);"))
    }

    @Test
    fun `claune host contract exposes compact prompt summary and detailed help`() {
        val summary = ClauneHostContract.promptSummary
        val topLevelHelp = ClauneHostContract.cliHelp()
        val locatorHelp = ClauneHostContract.cliHelp("locators")
        val debugHelp = ClauneHostContract.cliHelp("diagnostics")
        val typeHelp = ClauneHostContract.cliHelp("types")

        assertTrue(summary.contains("claune-js --help [topic]"))
        assertTrue(summary.contains("getByText"))
        assertTrue(summary.contains("Diagnostics exist only for API-gap debugging"))
        assertTrue(!summary.contains("interface ClauneHost"))
        assertTrue(topLevelHelp.contains("Usage:"))
        assertTrue(topLevelHelp.contains("claune-js - <<'JS'"))
        assertTrue(locatorHelp.contains("claune.getByText"))
        assertTrue(locatorHelp.contains("locator.click"))
        assertTrue(locatorHelp.contains("locator.describe"))
        assertTrue(locatorHelp.contains("claune.locator(\"*\")"))
        assertTrue(debugHelp.contains("claune.debug.observeScreen"))
        assertTrue(debugHelp.contains("Capture the latest screen interaction state"))
        assertTrue(typeHelp.contains("interface ClauneHost"))
    }

    @Test
    fun `generated host contract keeps bootstrap and types aligned`() {
        val declarations = ClauneHostContract.typeDefinitions
        val bootstrap = ClauneHostContract.bootstrapJavascript

        assertTrue(declarations.contains("interface ClauneHost"))
        assertTrue(declarations.contains("interface Locator"))
        assertTrue(declarations.contains("LocatorDescription"))
        assertTrue(declarations.contains("interface LocatorAssertions"))
        assertTrue(declarations.contains("interface ClauneDebugHost"))
        assertTrue(declarations.contains("getByText(text: LocatorText, options?: TextLocatorOptions): Locator;"))
        assertTrue(declarations.contains("getByLabel(text: LocatorText, options?: TextLocatorOptions): Locator;"))
        assertTrue(declarations.contains("getByRole(role: string, options?: RoleOptions): Locator;"))
        assertTrue(declarations.contains("getByTestId(testId: string): Locator;"))
        assertTrue(declarations.contains("expect(locator: Locator): LocatorAssertions;"))
        assertTrue(declarations.contains("isVisible(): boolean;"))
        assertTrue(declarations.contains("describe(options?: LocatorDescribeOptions): LocatorDescription;"))
        assertTrue(declarations.contains("current(): HostSuccessOutcome;"))
        assertTrue(declarations.contains("interface InstalledApp"))
        assertTrue(declarations.contains("label: string;"))
        assertTrue(declarations.contains("focusable: boolean;"))
        assertTrue(declarations.contains("type Bounds = [number, number, number, number];"))
        assertTrue(declarations.contains("interface ScreenInspection"))
        assertTrue(declarations.contains("interface VisibleElement"))
        assertTrue(declarations.contains("interface VisibleGroup"))
        assertTrue(declarations.contains("interface ActionAffordance"))
        assertTrue(declarations.contains("mode?: \"interactions\" | \"compact\" | \"full\";"))
        assertTrue(declarations.contains("interface RawNodeSearchResult"))
        assertTrue(declarations.contains("findRawNodes(options: RawNodeSearchOptions): RawNodeSearchResult;"))
        assertTrue(!declarations.contains("findGroup(screen: ScreenObservation, selector: InteractionSelector): VisibleGroup | null;"))
        assertTrue(declarations.contains("center: [number, number];"))
        assertTrue(declarations.contains("clickabilityReason: string;"))
        assertTrue(declarations.contains("inspectScreen(options: ScreenInspectOptions): ScreenInspection;"))
        assertTrue(declarations.contains("listInstalledApps(): InstalledApp[];"))
        assertTrue(declarations.contains("launchApp(packageName: string): HostSuccessOutcome;"))
        assertTrue(!declarations.contains("interface TapTextOptions"))
        assertTrue(!declarations.contains("interface EnterTextOptions"))
        assertTrue(!declarations.contains("tapText("))
        assertTrue(
            declarations.contains("waitForState(type: WaitStateType, value: WaitStateValue, timeoutMs: number): HostSuccessOutcome;"),
        )
        assertTrue(declarations.contains("tapPoint(x: number, y: number): HostSuccessOutcome;"))
        assertTrue(declarations.contains("tapBounds(bounds: Bounds): HostSuccessOutcome;"))
        assertTrue(!declarations.contains("focusSelector("))
        assertTrue(!declarations.contains("enterText("))
        assertTrue(declarations.contains("scrollRef(ref: string, direction: \"up\" | \"down\"): HostSuccessOutcome;"))
        assertTrue(declarations.contains("scrollScreen(direction: \"up\" | \"down\"): HostSuccessOutcome;"))
        assertTrue(!declarations.contains("typeIntoFocused("))
        assertTrue(bootstrap.contains("globalThis.claune = Object.freeze"))
        assertTrue(bootstrap.contains("getByText(text, options)"))
        assertTrue(bootstrap.contains("getByRole(role, options)"))
        assertTrue(bootstrap.contains("toBeVisible(options)"))
        assertTrue(bootstrap.contains("if (selector === \"*\") return { kind: \"all\" };"))
        assertTrue(bootstrap.contains("describe(options)"))
        assertTrue(bootstrap.contains("isVisible()"))
        assertTrue(bootstrap.contains("current() { return __clauneNative.deviceCurrent(); }"))
        assertTrue(bootstrap.contains("debug: Object.freeze(__clauneDebug)"))
        assertTrue(bootstrap.contains("__clauneRequireOutcome(\"locator.count\", __clauneNative.locatorCount(frozenSpec))"))
        assertTrue(bootstrap.contains("return JSON.parse(__clauneObserveScreenJson(JSON.stringify(options ?? {})));"))
        assertTrue(bootstrap.contains("return JSON.parse(__clauneInspectScreenJson(JSON.stringify(options ?? {})));"))
        assertTrue(bootstrap.contains("return JSON.parse(__clauneFindRawNodesJson(JSON.stringify(options ?? {})));"))
        assertTrue(bootstrap.contains("return JSON.parse(__clauneListInstalledAppsJson());"))
        assertTrue(bootstrap.contains("__clauneRequireOutcome(\"launchApp\", __clauneLaunchAppJson(String(packageName)));"))
        assertTrue(!bootstrap.contains("__clauneTapTextJson"))
        assertTrue(bootstrap.contains("__clauneRequireOutcome(\"tapPoint\", __clauneTapPointJson(Number(x), Number(y)));"))
        assertTrue(bootstrap.contains("__clauneRequireOutcome(\"tapBounds\", __clauneTapBoundsJson(JSON.stringify(bounds ?? [])));"))
        assertTrue(!bootstrap.contains("__claunePerformActionJson"))
        assertTrue(!bootstrap.contains("findGroup(screen, selector)"))
        assertTrue(!bootstrap.contains("__clauneLastScreen"))
        assertTrue(!bootstrap.contains("__clauneFocusSelectorJson"))
        assertTrue(!bootstrap.contains("__clauneEnterTextJson"))
        assertTrue(bootstrap.contains("__clauneRequireOutcome(\"scrollRef\", __clauneScrollRefJson(String(ref), String(direction)));"))
        assertTrue(bootstrap.contains("__clauneRequireOutcome(\"scrollScreen\", __clauneScrollScreenJson(String(direction)));"))
        assertTrue(!bootstrap.contains("__clauneTypeIntoFocusedJson"))
        assertTrue(!bootstrap.contains("__clauneObservePhoneJson()));"))
    }

    @Test
    fun `quickjs runtime executes locator count click and expect through generated bootstrap`() = runTest {
        val actuator = RuntimeFakePhoneActuator(tapResult = ActionResult.Success("Tapped."))
        val runtime =
            quickJsRuntimeOrSkip(
                observer = RuntimeFakePhoneObserver(
                    listOf(
                        runtimeSnapshot(
                            elements = listOf(
                                runtimeElement(id = "add", ref = "e1", label = "Add", text = "Add", role = "button"),
                                runtimeElement(id = "done", ref = "e2", label = "Done", text = "Done"),
                            ),
                        ),
                    ),
                ),
                actuator = actuator,
            )

        val result =
            runtime.runInline(
                """
                const count = claune.getByText("Add").count();
                const all = claune.locator("*").describe({ limit: 5 });
                const visible = claune.getByText("Add").isVisible();
                const current = claune.device.current();
                claune.getByRole("button", { name: "Add" }).click();
                claune.expect(claune.getByText("Done")).toBeVisible({ timeoutMs: 0 });
                return { count, allCount: all.count, visible, packageName: current.data.foregroundPackage };
                """.trimIndent(),
                argv = emptyList(),
                stdin = "",
            )

        assumeQuickJsResult(result)
        assertTrue(result.stdout.contains(""""count":1"""))
        assertTrue(result.stdout.contains(""""allCount":2"""))
        assertTrue(result.stdout.contains(""""visible":true"""))
        assertEquals("add", actuator.lastTapped?.elementId)
        assertEquals(
            listOf("locatorCount", "locatorDescribe", "locatorIsVisible", "deviceCurrent", "locatorClick", "locatorAssert"),
            result.hostCalls.map { it.name },
        )
    }

    @Test
    fun `quickjs locator errors expose errorCode and candidate data`() = runTest {
        val runtime =
            quickJsRuntimeOrSkip(
                observer = RuntimeFakePhoneObserver(
                    listOf(
                        runtimeSnapshot(
                            elements = listOf(
                                runtimeElement(id = "add-1", ref = "e1", label = "Add", text = "Add"),
                                runtimeElement(id = "add-2", ref = "e2", label = "Add", text = "Add"),
                            ),
                        ),
                    ),
                ),
            )

        val result =
            runtime.runInline(
                """
                try {
                  claune.getByText("Add").click({ timeoutMs: 0 });
                  return { ok: false };
                } catch (error) {
                  return { code: error.errorCode, callName: error.callName, count: error.data.count };
                }
                """.trimIndent(),
                argv = emptyList(),
                stdin = "",
            )

        assumeQuickJsResult(result)
        assertTrue(result.stdout.contains(""""code":"ambiguous_match""""))
        assertTrue(result.stdout.contains(""""callName":"locator.click""""))
        assertTrue(result.stdout.contains(""""count":2"""))
    }

    @Test
    fun `quickjs supported namespaces expose app discovery launch and device navigation`() = runTest {
        val registry =
            RuntimeFakeInstalledAppRegistry(
                listOf(
                    InstalledAppPayload(
                        label = "CRED",
                        packageName = "com.cred",
                        activityName = "MainActivity",
                    ),
                ),
            )
        val runtime = quickJsRuntimeOrSkip(installedAppRegistry = registry)

        val result =
            runtime.runInline(
                """
                const apps = claune.apps.list();
                claune.apps.launch(apps[0].packageName);
                claune.device.back();
                claune.device.home();
                return { packageName: apps[0].packageName };
                """.trimIndent(),
                argv = emptyList(),
                stdin = "",
            )

        assumeQuickJsResult(result)
        assertEquals("com.cred", registry.launchedPackage)
        assertTrue(result.stdout.contains(""""packageName":"com.cred""""))
        assertEquals(listOf("launchApp", "pressBack", "pressHome"), result.hostCalls.map { it.name })
    }

    @Test
    fun `quickjs locator extraction filter placeholder and enter press work through generated bootstrap`() = runTest {
        val actuator = RuntimeFakePhoneActuator(pressEnterResult = ActionResult.Success("Pressed Enter."))
        val runtime =
            quickJsRuntimeOrSkip(
                observer = RuntimeFakePhoneObserver(
                    listOf(
                        runtimeSnapshot(
                            elements = listOf(
                                runtimeElement(
                                    id = "search",
                                    ref = "e1",
                                    label = "Search",
                                    text = "Search",
                                    role = "input",
                                    editable = true,
                                ),
                                runtimeElement(id = "apple", ref = "e2", label = "Apple ₹180", text = "Apple ₹180", role = "listitem"),
                                runtimeElement(id = "banana", ref = "e3", label = "Banana ₹60", text = "Banana ₹60", role = "listitem"),
                            ),
                        ),
                    ),
                ),
                actuator = actuator,
            )

        val result =
            runtime.runInline(
                """
                claune.getByPlaceholder("Search").press("Enter", { timeoutMs: 0 });
                const apple = claune.getByRole("listitem").filter({ hasText: "Apple" }).textContent({ timeoutMs: 0 });
                const all = claune.getByRole("listitem").allTextContents();
                return { apple, all };
                """.trimIndent(),
                argv = emptyList(),
                stdin = "",
            )

        assumeQuickJsResult(result)
        assertEquals("search", actuator.lastPressedEnter?.elementId)
        assertTrue(result.stdout.contains(""""apple":"Apple ₹180""""))
        assertTrue(result.stdout.contains(""""Banana ₹60""""))
    }
}

private fun quickJsRuntime(
    observer: PhoneObserver = RuntimeFakePhoneObserver(listOf(runtimeSnapshot())),
    actuator: RuntimeFakePhoneActuator = RuntimeFakePhoneActuator(),
    installedAppRegistry: InstalledAppRegistry = EmptyInstalledAppRegistry,
    logStore: InMemorySessionLogStore = InMemorySessionLogStore(),
): QuickJsScriptRuntime = QuickJsScriptRuntime(
    phoneObserver = observer,
    phoneActuator = actuator,
    installedAppRegistry = installedAppRegistry,
    sessionCoordinator = runtimeSessionCoordinator(logStore),
    logStore = logStore,
    dispatcher = Dispatchers.Unconfined,
    now = { Instant.parse("2026-04-16T00:00:00Z") },
)

private fun quickJsRuntimeOrSkip(
    observer: PhoneObserver = RuntimeFakePhoneObserver(listOf(runtimeSnapshot())),
    actuator: RuntimeFakePhoneActuator = RuntimeFakePhoneActuator(),
    installedAppRegistry: InstalledAppRegistry = EmptyInstalledAppRegistry,
    logStore: InMemorySessionLogStore = InMemorySessionLogStore(),
): QuickJsScriptRuntime = try {
    quickJsRuntime(
        observer = observer,
        actuator = actuator,
        installedAppRegistry = installedAppRegistry,
        logStore = logStore,
    )
} catch (throwable: UnsatisfiedLinkError) {
    assumeNoException("QuickJS native runtime is unavailable in this unit-test environment.", throwable)
    throw throwable
}

private fun assumeQuickJsResult(result: ClauneJsResult) {
    if (result.exitCode != 0 && result.hostCalls.isEmpty()) {
        assumeNoException("QuickJS native runtime is unavailable in this unit-test environment.", AssertionError(result.stderr))
    }
    assertEquals(0, result.exitCode)
}

private fun runtimeSessionCoordinator(logStore: InMemorySessionLogStore): SessionCoordinator {
    val root = Files.createTempDirectory("claune-quickjs-runtime").toFile()
    val store = CodingSessionStore(cwd = root.absolutePath, agentDir = root.resolve("agent"))
    return SessionCoordinator(logStore, store)
}

private fun runtimeSnapshot(packageName: String = "com.example.app", elements: List<ScreenNode> = listOf(runtimeElement())): ScreenState {
    val children = elements.mapIndexed { index, node -> node.copy(path = listOf(index)) }
    val root = runtimeElement(
        id = "root",
        ref = "root",
        label = "Root",
        role = "root",
        clickable = false,
        bounds = listOf(0, 0, 1080, 2400),
        children = children,
    )
    return ScreenState(
        snapshotId = "snapshot",
        capturedAt = Instant.parse("2026-04-16T00:00:00Z").toString(),
        foregroundPackage = packageName,
        root = root,
    )
}

private fun runtimeElement(
    id: String = "el-1",
    ref: String = id,
    label: String = "Action",
    text: String? = null,
    role: String = "button",
    clickable: Boolean = true,
    editable: Boolean = false,
    bounds: List<Int> = listOf(0, 0, 100, 100),
    children: List<ScreenNode> = emptyList(),
): ScreenNode = ScreenNode(
    path = emptyList(),
    ref = ref,
    elementId = id,
    role = role,
    label = label,
    text = text,
    visibleToUser = true,
    clickable = clickable,
    editable = editable,
    focused = false,
    bounds = bounds,
    children = children,
)

private class RuntimeFakePhoneObserver(private val snapshots: List<ScreenState>) : PhoneObserver {
    private var index = 0

    override suspend fun captureScreenState(): ScreenState {
        val resolvedIndex = index.coerceAtMost(snapshots.lastIndex)
        val snapshot = snapshots[resolvedIndex]
        if (index < snapshots.lastIndex) {
            index += 1
        }
        return snapshot
    }
}

private class RuntimeFakePhoneActuator(
    private val tapResult: ActionResult = ActionResult.Blocked("No tap configured."),
    private val typeResult: ActionResult = ActionResult.Blocked("No typing configured."),
    private val scrollResult: ActionResult = ActionResult.Blocked("No scroll configured."),
    private val pressEnterResult: ActionResult = ActionResult.Success("Pressed Enter."),
) : PhoneActuator {
    var lastTapped: ElementRef? = null
    var lastTyped: Pair<ElementRef, String>? = null
    var lastScrolled: Pair<ElementRef, ScrollDirection>? = null
    var lastPressedEnter: ElementRef? = null

    override suspend fun tap(target: ElementRef): ActionResult {
        lastTapped = target
        return tapResult
    }

    override suspend fun tapPoint(x: Int, y: Int): ActionResult = tapResult

    override suspend fun type(target: ElementRef, text: String): ActionResult {
        lastTyped = target to text
        return typeResult
    }

    override suspend fun typeFocused(text: String): ActionResult = typeResult

    override suspend fun scroll(target: ElementRef, direction: ScrollDirection): ActionResult {
        lastScrolled = target to direction
        return scrollResult
    }

    override suspend fun pressBack(): ActionResult = ActionResult.Success("Pressed back.")

    override suspend fun pressHome(): ActionResult = ActionResult.Success("Pressed home.")

    override suspend fun pressEnter(target: ElementRef): ActionResult {
        lastPressedEnter = target
        return pressEnterResult
    }
}

private class RuntimeFakeInstalledAppRegistry(private val apps: List<InstalledAppPayload>) : InstalledAppRegistry {
    var launchedPackage: String? = null

    override fun listLaunchableApps(): List<InstalledAppPayload> = apps

    override fun launchPackage(packageName: String): HostCallOutcome {
        launchedPackage = packageName
        return HostCallOutcome(ok = true, message = "Launched package '$packageName'.")
    }
}
