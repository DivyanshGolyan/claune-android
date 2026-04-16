package com.divyanshgolyan.claune.android.ui

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.divyanshgolyan.claune.android.app.clauneContainer
import com.divyanshgolyan.claune.android.scripting.ScriptExecutionRequest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DemoModeSmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun appLaunchesOnApi31Emulator() {
        composeRule.onNodeWithText("Claune Android").fetchSemanticsNode()
        composeRule.onNodeWithText("Latest runtime state").fetchSemanticsNode()
    }

    @Test
    fun demoPhoneScriptSampleRunsSuccessfully() {
        composeRule.onNodeWithText("Claune Android").fetchSemanticsNode()

        val result =
            runBlocking {
                val container = composeRule.activity.clauneContainer()
                container.setDemoPhoneEnabled(true)
                container.scriptRuntime.execute(
                    ScriptExecutionRequest(
                        script = DEMO_SCRIPT_SAMPLE,
                        source = "android_test",
                    ),
                )
            }

        val debugMessage =
            "ok=${result.ok}, summary=${result.summary}, error=${result.error}, " +
                "hostCalls=${result.hostCalls.size}, data=${result.data}"

        assertTrue(debugMessage, result.ok)
        assertEquals(debugMessage, 4, result.hostCalls.size)
        assertTrue(debugMessage, result.data.toString().contains("Saved networks"))
        assertTrue(debugMessage, result.data.toString().contains("com.demo.launcher"))
        assertTrue(debugMessage, result.data.toString().contains("com.android.settings"))
    }

    private companion object {
        private const val DEMO_SCRIPT_SAMPLE =
            """
            const launcherSnapshot = claune.observePhone();
            const openSettings = claune.tapElement("demo|launcher|settings");
            const settingsReady = claune.waitForState("package", "com.android.settings", 1000);
            const openWifi = claune.tapElement("demo|settings|network_internet");
            const wifiReady = claune.waitForState("text", "Saved networks", 1000);

            return {
              launcherPackage: launcherSnapshot.foregroundPackage,
              openSettings,
              settingsReady,
              openWifi,
              wifiReady,
              finalScreen: claune.observePhone().visibleText,
            };
            """
    }
}
