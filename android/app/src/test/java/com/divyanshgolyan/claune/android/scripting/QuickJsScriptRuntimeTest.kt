package com.divyanshgolyan.claune.android.scripting

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickJsScriptRuntimeTest {
    @Test
    fun `script source validator allows console usage`() {
        val unsupported =
            ScriptSourceValidator.firstUnsupportedFeature(
                """
                const screen = claune.observeScreen();
                console.log(screen.foregroundPackage);
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
                const screen = claune.observeScreen();
                claune.pressHome();
                return { foregroundPackage: screen.foregroundPackage };
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
        val functionHelp = ClauneHostContract.cliHelp("observeScreen")
        val typeHelp = ClauneHostContract.cliHelp("types")

        assertTrue(summary.contains("claune-js --help [topic]"))
        assertTrue(summary.contains("observeScreen"))
        assertTrue(!summary.contains("interface ClauneHost"))
        assertTrue(topLevelHelp.contains("Usage:"))
        assertTrue(topLevelHelp.contains("claune-js - <<'JS'"))
        assertTrue(functionHelp.contains("claune.observeScreen"))
        assertTrue(functionHelp.contains("Capture the latest screen interaction state"))
        assertTrue(typeHelp.contains("interface ClauneHost"))
    }

    @Test
    fun `generated host contract keeps bootstrap and types aligned`() {
        val declarations = ClauneHostContract.typeDefinitions
        val bootstrap = ClauneHostContract.bootstrapJavascript

        ClauneHostContract.exposedMethodNames.forEach { methodName ->
            assertTrue(declarations.contains("$methodName("))
            assertTrue(bootstrap.contains("$methodName("))
        }
        assertTrue(declarations.contains("interface ClauneHost"))
        assertTrue(declarations.contains("interface InstalledApp"))
        assertTrue(declarations.contains("label?: string;"))
        assertTrue(declarations.contains("focusable: boolean;"))
        assertTrue(declarations.contains("focusable?: boolean;"))
        assertTrue(declarations.contains("type Bounds = [number, number, number, number];"))
        assertTrue(declarations.contains("interface ScreenInspection"))
        assertTrue(declarations.contains("interface VisibleElement"))
        assertTrue(declarations.contains("interface VisibleGroup"))
        assertTrue(declarations.contains("interface ActionAffordance"))
        assertTrue(declarations.contains("mode?: \"interactions\" | \"compact\" | \"full\";"))
        assertTrue(declarations.contains("interface RawNodeSearchResult"))
        assertTrue(declarations.contains("findRawNodes(options: RawNodeSearchOptions): RawNodeSearchResult;"))
        assertTrue(declarations.contains("findGroup(screen: ScreenObservation, selector: InteractionSelector): VisibleGroup | null;"))
        assertTrue(declarations.contains("performAction(actionId: string): HostSuccessOutcome;"))
        assertTrue(declarations.contains("center: [number, number];"))
        assertTrue(declarations.contains("clickabilityReason: string;"))
        assertTrue(declarations.contains("inspectScreen(options: ScreenInspectOptions): ScreenInspection;"))
        assertTrue(declarations.contains("listInstalledApps(): InstalledApp[];"))
        assertTrue(declarations.contains("launchApp(packageName: string): HostSuccessOutcome;"))
        assertTrue(declarations.contains("interface TapTextOptions"))
        assertTrue(declarations.contains("tapText(text: string, options?: boolean | TapTextOptions, first?: boolean): HostSuccessOutcome;"))
        assertTrue(
            declarations.contains("waitForState(type: WaitStateType, value: WaitStateValue, timeoutMs: number): HostSuccessOutcome;"),
        )
        assertTrue(declarations.contains("tapPoint(x: number, y: number): HostSuccessOutcome;"))
        assertTrue(declarations.contains("tapBounds(bounds: Bounds): HostSuccessOutcome;"))
        assertTrue(declarations.contains("focusSelector(selector: ElementSelector, timeoutMs: number): HostSuccessOutcome;"))
        assertTrue(declarations.contains("scrollRef(ref: string, direction: \"up\" | \"down\"): HostSuccessOutcome;"))
        assertTrue(declarations.contains("scrollScreen(direction: \"up\" | \"down\"): HostSuccessOutcome;"))
        assertTrue(declarations.contains("typeIntoFocused(text: string): HostSuccessOutcome;"))
        assertTrue(bootstrap.contains("globalThis.claune = Object.freeze"))
        assertTrue(bootstrap.contains("return JSON.parse(__clauneObserveScreenJson(JSON.stringify(options ?? {})));"))
        assertTrue(bootstrap.contains("return JSON.parse(__clauneInspectScreenJson(JSON.stringify(options ?? {})));"))
        assertTrue(bootstrap.contains("return JSON.parse(__clauneFindRawNodesJson(JSON.stringify(options ?? {})));"))
        assertTrue(bootstrap.contains("return JSON.parse(__clauneListInstalledAppsJson());"))
        assertTrue(bootstrap.contains("__clauneRequireOutcome(\"launchApp\", __clauneLaunchAppJson(String(packageName)));"))
        assertTrue(
            bootstrap.contains(
                "__clauneRequireOutcome(\"tapText\", __clauneTapTextJson(String(text), __clauneTapTextExact(options), __clauneTapTextFirst(options, first)));",
            ),
        )
        assertTrue(bootstrap.contains("__clauneRequireOutcome(\"tapPoint\", __clauneTapPointJson(Number(x), Number(y)));"))
        assertTrue(bootstrap.contains("__clauneRequireOutcome(\"tapBounds\", __clauneTapBoundsJson(JSON.stringify(bounds ?? [])));"))
        assertTrue(bootstrap.contains("__clauneRequireOutcome(\"performAction\", __claunePerformActionJson(String(actionId)));"))
        assertTrue(bootstrap.contains("findGroup(screen, selector)"))
        assertTrue(bootstrap.contains("__clauneLastScreen = screen;"))
        assertTrue(
            bootstrap.contains(
                "__clauneRequireOutcome(\"focusSelector\", __clauneFocusSelectorJson(JSON.stringify(selector ?? {}), Number(timeoutMs ?? 0)));",
            ),
        )
        assertTrue(bootstrap.contains("__clauneRequireOutcome(\"scrollRef\", __clauneScrollRefJson(String(ref), String(direction)));"))
        assertTrue(bootstrap.contains("__clauneRequireOutcome(\"scrollScreen\", __clauneScrollScreenJson(String(direction)));"))
        assertTrue(bootstrap.contains("__clauneRequireOutcome(\"typeIntoFocused\", __clauneTypeIntoFocusedJson(String(text)));"))
        assertTrue(!bootstrap.contains("__clauneObservePhoneJson()));"))
    }
}
