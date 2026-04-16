package com.divyanshgolyan.claune.android.phone

import com.divyanshgolyan.claune.android.runtime.ActionResult
import com.divyanshgolyan.claune.android.runtime.ElementRef
import com.divyanshgolyan.claune.android.runtime.ScrollDirection
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoPhoneBridgeTest {
    @Test
    fun `launcher snapshot exposes settings button`() = runTest {
        val bridge = DemoPhoneBridge()

        val snapshot = bridge.captureSnapshot()

        assertEquals("com.demo.launcher", snapshot.foregroundPackage)
        assertTrue(snapshot.actionableElements.any { it.label == "Settings" })
    }

    @Test
    fun `tap flow can navigate from launcher to wifi page`() = runTest {
        val bridge = DemoPhoneBridge()

        val openSettings = bridge.tap(ElementRef("demo|launcher|settings"))
        val settingsSnapshot = bridge.captureSnapshot()
        val openWifi = bridge.tap(ElementRef("demo|settings|network_internet"))
        val wifiSnapshot = bridge.captureSnapshot()

        assertTrue(openSettings is ActionResult.Success)
        assertEquals("com.android.settings", settingsSnapshot.foregroundPackage)
        assertTrue(openWifi is ActionResult.Success)
        assertTrue(wifiSnapshot.visibleText.contains("Saved networks"))
    }

    @Test
    fun `search field accepts text and reveals wifi result`() = runTest {
        val bridge = DemoPhoneBridge()
        bridge.tap(ElementRef("demo|launcher|settings"))
        bridge.tap(ElementRef("demo|settings|search"))

        val typeResult = bridge.type(ElementRef("demo|settings|search_input"), "wifi")
        val snapshot = bridge.captureSnapshot()

        assertTrue(typeResult is ActionResult.Success)
        assertTrue(snapshot.actionableElements.any { it.id == "demo|settings_search|wifi" })
    }

    @Test
    fun `settings list scroll reveals lower rows`() = runTest {
        val bridge = DemoPhoneBridge()
        bridge.tap(ElementRef("demo|launcher|settings"))

        val scrollResult = bridge.scroll(ElementRef("demo|settings|list"), ScrollDirection.Down)
        val snapshot = bridge.captureSnapshot()

        assertTrue(scrollResult is ActionResult.Success)
        assertTrue(snapshot.visibleText.contains("Battery"))
        assertTrue(snapshot.visibleText.contains("System"))
    }

    @Test
    fun `press home resets demo state`() = runTest {
        val bridge = DemoPhoneBridge()
        bridge.tap(ElementRef("demo|launcher|settings"))
        bridge.tap(ElementRef("demo|settings|apps"))

        val result = bridge.pressHome()
        val snapshot = bridge.captureSnapshot()

        assertTrue(result is ActionResult.Success)
        assertEquals("com.demo.launcher", snapshot.foregroundPackage)
    }
}
