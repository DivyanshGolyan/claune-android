package com.divyanshgolyan.claune.android.scripting

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickJsScriptRuntimeTest {
    @Test
    fun `script source validator rejects await with explicit error`() {
        val unsupported =
            ScriptSourceValidator.firstUnsupportedFeature(
                """
                const state = await claune.observePhone();
                return state;
                """.trimIndent(),
            )

        assertEquals("await", unsupported?.name)
        assertEquals(
            "unsupported_syntax: top-level await is not supported; claune APIs are synchronous plain function calls",
            unsupported?.error,
        )
    }

    @Test
    fun `script source validator rejects unsupported runtime features`() {
        assertEquals(
            "console",
            ScriptSourceValidator.firstUnsupportedFeature("console.log('hi'); return {};")?.name,
        )
        assertEquals(
            "module",
            ScriptSourceValidator.firstUnsupportedFeature("import foo from 'bar'; return foo;")?.name,
        )
        assertEquals(
            "promise",
            ScriptSourceValidator.firstUnsupportedFeature("return Promise.resolve({ ok: true });")?.name,
        )
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
        assertTrue(bootstrap.contains("globalThis.claune = Object.freeze"))
    }
}
