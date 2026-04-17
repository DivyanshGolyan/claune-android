package com.divyanshgolyan.claune.android.scripting

import com.divyanshgolyan.claune.android.data.local.InMemorySessionLogStore
import com.divyanshgolyan.claune.android.runtime.ActionResult
import com.divyanshgolyan.claune.android.runtime.ElementRef
import com.divyanshgolyan.claune.android.runtime.PhoneActuator
import com.divyanshgolyan.claune.android.runtime.PhoneObserver
import com.divyanshgolyan.claune.android.runtime.ScrollDirection
import com.divyanshgolyan.claune.android.runtime.SessionCoordinator
import com.divyanshgolyan.claune.android.runtime.UiElement
import com.divyanshgolyan.claune.android.runtime.UiSnapshot
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScriptHostTest {
    @Test
    fun `observePhone returns snapshot payload and records snapshot`() = runTest {
        val logStore = InMemorySessionLogStore()
        val coordinator = SessionCoordinator(logStore)
        val snapshot =
            UiSnapshot(
                snapshotId = "snapshot-1",
                capturedAt = Instant.parse("2026-04-16T00:00:00Z"),
                foregroundPackage = "com.android.settings",
                visibleText = listOf("Settings"),
                actionableElements =
                listOf(
                    UiElement(
                        id = "el-1",
                        role = "button",
                        label = "Wi-Fi",
                        clickable = true,
                        editable = false,
                        focused = false,
                        bounds = listOf(0, 0, 100, 100),
                    ),
                ),
                focusedElementId = null,
            )
        val host =
            ScriptHost(
                scriptExecutionId = "script-1",
                phoneObserver = FakePhoneObserver(listOf(snapshot)),
                phoneActuator = FakePhoneActuator(),
                sessionCoordinator = coordinator,
                logStore = logStore,
            )

        val payload = host.observePhone()

        assertEquals("com.android.settings", payload.foregroundPackage)
        assertEquals(1, payload.actionableElements.size)
        assertEquals("el-1", payload.actionableElements.first().ref)
        assertEquals(1, logStore.recentSnapshots().size)
    }

    @Test
    fun `waitForState succeeds when later snapshot matches package`() = runTest {
        var currentTime = Instant.parse("2026-04-16T00:00:00Z")
        val logStore = InMemorySessionLogStore()
        val coordinator = SessionCoordinator(logStore)
        val host =
            ScriptHost(
                scriptExecutionId = "script-1",
                phoneObserver =
                FakePhoneObserver(
                    listOf(
                        snapshot(packageName = "com.one.app"),
                        snapshot(packageName = "com.two.app"),
                    ),
                ),
                phoneActuator = FakePhoneActuator(),
                sessionCoordinator = coordinator,
                logStore = logStore,
                now = { currentTime },
                sleeper = { delayMs -> currentTime = currentTime.plusMillis(delayMs) },
            )

        val result = host.waitForState(type = "package", value = "com.two.app", timeoutMs = 600)

        assertTrue(result.ok)
        assertEquals("Matched package condition for 'com.two.app'.", result.message)
    }

    @Test
    fun `waitForState times out cleanly`() = runTest {
        var currentTime = Instant.parse("2026-04-16T00:00:00Z")
        val logStore = InMemorySessionLogStore()
        val coordinator = SessionCoordinator(logStore)
        val host =
            ScriptHost(
                scriptExecutionId = "script-1",
                phoneObserver = FakePhoneObserver(listOf(snapshot(packageName = "com.one.app"))),
                phoneActuator = FakePhoneActuator(),
                sessionCoordinator = coordinator,
                logStore = logStore,
                now = { currentTime },
                sleeper = { delayMs -> currentTime = currentTime.plusMillis(delayMs) },
            )

        val result = host.waitForState(type = "package", value = "com.two.app", timeoutMs = 300)

        assertFalse(result.ok)
        assertTrue(result.message.contains("Timed out waiting for package 'com.two.app'"))
    }

    @Test
    fun `tapElement delegates to actuator and records host call`() = runTest {
        val logStore = InMemorySessionLogStore()
        val coordinator = SessionCoordinator(logStore)
        val actuator = FakePhoneActuator(tapResult = ActionResult.Success("Tapped element."))
        val host =
            ScriptHost(
                scriptExecutionId = "script-1",
                phoneObserver = FakePhoneObserver(listOf(snapshot())),
                phoneActuator = actuator,
                sessionCoordinator = coordinator,
                logStore = logStore,
            )

        val result = host.tapElement("el-1")

        assertTrue(result.ok)
        assertEquals("el-1", actuator.lastTapped?.elementId)
        assertEquals(1, logStore.recentHostCalls().size)
    }

    @Test
    fun `tapSelector matches element by text and avoids positional guessing`() = runTest {
        val actuator = FakePhoneActuator(tapResult = ActionResult.Success("Tapped selector match."))
        val host =
            ScriptHost(
                scriptExecutionId = "script-1",
                phoneObserver =
                FakePhoneObserver(
                    listOf(
                        snapshot(
                            elements =
                            listOf(
                                element(id = "about-phone", ref = "e0", label = "About phone", text = "About phone"),
                                element(id = "wifi", ref = "e1", label = "Wi-Fi", text = "Wi-Fi"),
                            ),
                        ),
                    ),
                ),
                phoneActuator = actuator,
                sessionCoordinator = SessionCoordinator(InMemorySessionLogStore()),
                logStore = InMemorySessionLogStore(),
            )

        val result = host.tapSelector("""{"text":"Wi-Fi"}""")

        assertTrue(result.ok)
        assertEquals("wifi", actuator.lastTapped?.elementId)
    }

    @Test
    fun `typeIntoFocused targets the focused editable element`() = runTest {
        val actuator = FakePhoneActuator(typeResult = ActionResult.Success("Typed into focused field."))
        val host =
            ScriptHost(
                scriptExecutionId = "script-1",
                phoneObserver =
                FakePhoneObserver(
                    listOf(
                        snapshot(
                            focusedElementId = "search-box",
                            elements =
                            listOf(
                                element(id = "search-box", ref = "e1", label = "Search", role = "input", editable = true, focused = true),
                                element(id = "cta", ref = "e2", label = "Go"),
                            ),
                        ),
                    ),
                ),
                phoneActuator = actuator,
                sessionCoordinator = SessionCoordinator(InMemorySessionLogStore()),
                logStore = InMemorySessionLogStore(),
            )

        val result = host.typeIntoFocused("apple")

        assertTrue(result.ok)
        assertEquals("search-box", actuator.lastTyped?.first?.elementId)
        assertEquals("apple", actuator.lastTyped?.second)
    }

    @Test
    fun `waitForSelector succeeds when later snapshot exposes matching ref`() = runTest {
        var currentTime = Instant.parse("2026-04-16T00:00:00Z")
        val host =
            ScriptHost(
                scriptExecutionId = "script-1",
                phoneObserver =
                FakePhoneObserver(
                    listOf(
                        snapshot(elements = listOf(element(id = "about-phone", ref = "e0", label = "About phone"))),
                        snapshot(elements = listOf(element(id = "wifi", ref = "e1", label = "Wi-Fi", text = "Wi-Fi"))),
                    ),
                ),
                phoneActuator = FakePhoneActuator(),
                sessionCoordinator = SessionCoordinator(InMemorySessionLogStore()),
                logStore = InMemorySessionLogStore(),
                now = { currentTime },
                sleeper = { delayMs -> currentTime = currentTime.plusMillis(delayMs) },
            )

        val result = host.waitForSelector("""{"text":"Wi-Fi"}""", timeoutMs = 600)

        assertTrue(result.ok)
        assertTrue(result.message.contains("Matched selector"))
    }

    @Test
    fun `scrollContainer blocks unsupported horizontal direction`() = runTest {
        val host =
            ScriptHost(
                scriptExecutionId = "script-1",
                phoneObserver = FakePhoneObserver(listOf(snapshot())),
                phoneActuator = FakePhoneActuator(),
                sessionCoordinator = SessionCoordinator(InMemorySessionLogStore()),
                logStore = InMemorySessionLogStore(),
            )

        val result = host.scrollContainer("el-1", "sideways")

        assertFalse(result.ok)
        assertEquals("Unsupported scroll direction 'sideways'.", result.message)
    }

    private fun snapshot(
        packageName: String = "com.example.app",
        elements: List<UiElement> = listOf(element()),
        focusedElementId: String? = null,
    ): UiSnapshot = UiSnapshot(
        snapshotId = "snapshot",
        capturedAt = Instant.parse("2026-04-16T00:00:00Z"),
        foregroundPackage = packageName,
        visibleText = listOf("Alpha"),
        actionableElements = elements,
        focusedElementId = focusedElementId,
    )

    private fun element(
        id: String = "el-1",
        ref: String = id,
        label: String = "Action",
        text: String? = null,
        role: String = "button",
        editable: Boolean = false,
        focused: Boolean = false,
    ): UiElement = UiElement(
        id = id,
        ref = ref,
        role = role,
        label = label,
        text = text,
        clickable = true,
        editable = editable,
        focused = focused,
        bounds = listOf(0, 0, 100, 100),
    )
}

private class FakePhoneObserver(private val snapshots: List<UiSnapshot>) : PhoneObserver {
    private var index = 0

    override suspend fun captureSnapshot(): UiSnapshot {
        val resolvedIndex = index.coerceAtMost(snapshots.lastIndex)
        val snapshot = snapshots[resolvedIndex]
        if (index < snapshots.lastIndex) {
            index += 1
        }
        return snapshot
    }
}

private class FakePhoneActuator(
    private val tapResult: ActionResult = ActionResult.Blocked("No tap configured."),
    private val typeResult: ActionResult = ActionResult.Blocked("No typing configured."),
    private val scrollResult: ActionResult = ActionResult.Blocked("No scroll configured."),
) : PhoneActuator {
    var lastTapped: ElementRef? = null
    var lastTyped: Pair<ElementRef, String>? = null
    var lastScrolled: Pair<ElementRef, ScrollDirection>? = null

    override suspend fun tap(target: ElementRef): ActionResult {
        lastTapped = target
        return tapResult
    }

    override suspend fun type(target: ElementRef, text: String): ActionResult {
        lastTyped = target to text
        return typeResult
    }

    override suspend fun scroll(target: ElementRef, direction: ScrollDirection): ActionResult {
        lastScrolled = target to direction
        return scrollResult
    }

    override suspend fun pressBack(): ActionResult = ActionResult.Success("Pressed back.")

    override suspend fun pressHome(): ActionResult = ActionResult.Success("Pressed home.")
}
