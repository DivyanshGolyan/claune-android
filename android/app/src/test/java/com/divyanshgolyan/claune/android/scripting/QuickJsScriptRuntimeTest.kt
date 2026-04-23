package com.divyanshgolyan.claune.android.scripting

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickJsScriptRuntimeTest {
    @Test
    fun `script source validator rejects console usage through the AST`() {
        val unsupported =
            ScriptSourceValidator.firstUnsupportedFeature(
                """
                const screen = claune.observePhone();
                console.log(screen.foregroundPackage);
                return {};
                """.trimIndent(),
            )

        assertEquals("console", unsupported?.name)
        assertEquals(
            "unsupported_api: console is not available in Claune scripts; return compact data instead",
            unsupported?.error,
        )
    }

    @Test
    fun `script source validator rejects Promise usage through the AST`() {
        assertEquals(
            "promise",
            ScriptSourceValidator.firstUnsupportedFeature("return Promise.resolve({ ok: true });")?.name,
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
                const screen = claune.observePhone();
                claune.pressHome();
                return { foregroundPackage: screen.foregroundPackage };
                """.trimIndent(),
            )

        assertNull(unsupported)
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
        assertTrue(declarations.contains("listInstalledApps(): InstalledApp[];"))
        assertTrue(declarations.contains("launchApp(packageName: string): HostSuccessOutcome;"))
        assertTrue(declarations.contains("tapText(text: string, exact: boolean): HostSuccessOutcome;"))
        assertTrue(declarations.contains("focusSelector(selector: ElementSelector, timeoutMs: number): HostSuccessOutcome;"))
        assertTrue(declarations.contains("scrollRef(ref: string, direction: \"up\" | \"down\"): HostSuccessOutcome;"))
        assertTrue(declarations.contains("scrollScreen(direction: \"up\" | \"down\"): HostSuccessOutcome;"))
        assertTrue(declarations.contains("typeIntoFocused(text: string): HostSuccessOutcome;"))
        assertTrue(bootstrap.contains("globalThis.claune = Object.freeze"))
        assertTrue(bootstrap.contains("return JSON.parse(__clauneObservePhoneJson());"))
        assertTrue(bootstrap.contains("return JSON.parse(__clauneListInstalledAppsJson());"))
        assertTrue(bootstrap.contains("__clauneRequireOutcome(\"launchApp\", __clauneLaunchAppJson(String(packageName)));"))
        assertTrue(bootstrap.contains("__clauneRequireOutcome(\"tapText\", __clauneTapTextJson(String(text), Boolean(exact ?? true)));"))
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
