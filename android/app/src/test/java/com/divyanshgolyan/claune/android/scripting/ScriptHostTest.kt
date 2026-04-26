package com.divyanshgolyan.claune.android.scripting

import com.divyanshgolyan.claune.android.data.local.CodingSessionStore
import com.divyanshgolyan.claune.android.data.local.InMemorySessionLogStore
import com.divyanshgolyan.claune.android.runtime.ActionResult
import com.divyanshgolyan.claune.android.runtime.ElementRef
import com.divyanshgolyan.claune.android.runtime.PhoneActuator
import com.divyanshgolyan.claune.android.runtime.PhoneObserver
import com.divyanshgolyan.claune.android.runtime.ScrollDirection
import com.divyanshgolyan.claune.android.runtime.SessionCoordinator
import com.divyanshgolyan.claune.android.runtime.UiElement
import com.divyanshgolyan.claune.android.runtime.UiSnapshot
import com.divyanshgolyan.claune.android.runtime.WindowCandidate
import java.nio.file.Files
import java.time.Instant
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScriptHostTest {
    @Test
    fun `observePhone returns snapshot payload and records snapshot`() = runTest {
        val logStore = InMemorySessionLogStore()
        val coordinator = testSessionCoordinator(logStore)
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
        val coordinator = testSessionCoordinator(logStore)
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
        val coordinator = testSessionCoordinator(logStore)
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
    fun `listInstalledApps returns launchable app inventory and records host call`() = runTest {
        val logStore = InMemorySessionLogStore()
        val host =
            ScriptHost(
                scriptExecutionId = "script-1",
                phoneObserver = FakePhoneObserver(listOf(snapshot())),
                phoneActuator = FakePhoneActuator(),
                installedAppRegistry =
                FakeInstalledAppRegistry(
                    apps = listOf(
                        InstalledAppPayload(
                            label = "CRED",
                            packageName = "com.dreamplug.androidapp",
                            activityName = "com.dreamplug.androidapp.MainActivity",
                        ),
                    ),
                ),
                sessionCoordinator = testSessionCoordinator(logStore),
                logStore = logStore,
            )

        val apps = host.listInstalledApps()

        assertEquals("CRED", apps.single().label)
        assertEquals("com.dreamplug.androidapp", apps.single().packageName)
        assertEquals(1, logStore.recentHostCalls().size)
        assertEquals("listInstalledApps", logStore.recentHostCalls().single().name)
    }

    @Test
    fun `launchApp verifies target package became foreground`() = runTest {
        val logStore = InMemorySessionLogStore()
        val registry = FakeInstalledAppRegistry()
        val host =
            ScriptHost(
                scriptExecutionId = "script-1",
                phoneObserver = FakePhoneObserver(listOf(snapshot(packageName = "com.dreamplug.androidapp"))),
                phoneActuator = FakePhoneActuator(),
                installedAppRegistry = registry,
                sessionCoordinator = testSessionCoordinator(logStore),
                logStore = logStore,
            )

        val result = host.launchApp("com.dreamplug.androidapp")

        assertTrue(result.ok)
        assertEquals("com.dreamplug.androidapp", registry.launchedPackage)
        assertEquals("launchApp", logStore.recentHostCalls().single().name)
    }

    @Test
    fun `launchApp reports blocked activity start when package never becomes foreground`() = runTest {
        var currentTime = Instant.parse("2026-04-16T00:00:00Z")
        val logStore = InMemorySessionLogStore()
        val host =
            ScriptHost(
                scriptExecutionId = "script-1",
                phoneObserver = FakePhoneObserver(listOf(snapshot(packageName = "com.mi.android.globallauncher"))),
                phoneActuator = FakePhoneActuator(),
                installedAppRegistry = FakeInstalledAppRegistry(),
                sessionCoordinator = testSessionCoordinator(logStore),
                logStore = logStore,
                now = { currentTime },
                sleeper = { delayMs -> currentTime = currentTime.plusMillis(delayMs) },
            )

        val result = host.launchApp("in.swiggy.android")

        assertFalse(result.ok)
        assertTrue(result.message.contains("Android may have blocked the activity start"))
        assertEquals(
            "com.mi.android.globallauncher",
            result.data?.jsonObject?.get("observedForegroundPackage")?.jsonPrimitive?.content,
        )
    }

    @Test
    fun `launchApp surfaces when target app is present but not selected`() = runTest {
        var currentTime = Instant.parse("2026-04-16T00:00:00Z")
        val logStore = InMemorySessionLogStore()
        val host =
            ScriptHost(
                scriptExecutionId = "script-1",
                phoneObserver =
                FakePhoneObserver(
                    listOf(
                        snapshot(
                            packageName = "com.divyanshgolyan.claune.android",
                            windowCandidates =
                            listOf(
                                WindowCandidate(
                                    packageName = "in.swiggy.android",
                                    className = "android.widget.FrameLayout",
                                    type = "APPLICATION",
                                    layer = 0,
                                    active = false,
                                    focused = false,
                                    bounds = listOf(0, 0, 1080, 2400),
                                    visibleText = listOf("Swiggy"),
                                    actionableElementCount = 12,
                                ),
                            ),
                            selectedWindowReason = "Selected Claune app root because no better external app window was available.",
                        ),
                    ),
                ),
                phoneActuator = FakePhoneActuator(),
                installedAppRegistry = FakeInstalledAppRegistry(),
                sessionCoordinator = testSessionCoordinator(logStore),
                logStore = logStore,
                now = { currentTime },
                sleeper = { delayMs -> currentTime = currentTime.plusMillis(delayMs) },
            )

        val result = host.launchApp("in.swiggy.android")

        assertFalse(result.ok)
        assertTrue(result.message.contains("present in accessibility windows"))
        assertEquals(
            "true",
            result.data?.jsonObject?.get("targetWindowVisible")?.jsonPrimitive?.content,
        )
    }

    @Test
    fun `tapElement delegates to actuator and records host call`() = runTest {
        val logStore = InMemorySessionLogStore()
        val coordinator = testSessionCoordinator(logStore)
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
                sessionCoordinator = testSessionCoordinator(InMemorySessionLogStore()),
                logStore = InMemorySessionLogStore(),
            )

        val result = host.tapSelector("""{"text":"Wi-Fi"}""")

        assertTrue(result.ok)
        assertEquals("wifi", actuator.lastTapped?.elementId)
    }

    @Test
    fun `tapSelector accepts label selectors because labels are exposed in snapshots`() = runTest {
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
                                element(id = "wifi", ref = "e1", label = "Wi-Fi", text = null),
                            ),
                        ),
                    ),
                ),
                phoneActuator = actuator,
                sessionCoordinator = testSessionCoordinator(InMemorySessionLogStore()),
                logStore = InMemorySessionLogStore(),
            )

        val result = host.tapSelector("""{"label":"Wi-Fi"}""")

        assertTrue(result.ok)
        assertEquals("wifi", actuator.lastTapped?.elementId)
    }

    @Test
    fun `tapText prefers exact visible text without relying on refs`() = runTest {
        val actuator = FakePhoneActuator(tapResult = ActionResult.Success("Tapped visible text match."))
        val host =
            ScriptHost(
                scriptExecutionId = "script-1",
                phoneObserver =
                FakePhoneObserver(
                    listOf(
                        snapshot(
                            elements =
                            listOf(
                                element(id = "display", ref = "e0", label = "Display", text = "Display"),
                                element(id = "wifi", ref = "e1", label = "Wi-Fi", text = "Wi-Fi"),
                            ),
                        ),
                    ),
                ),
                phoneActuator = actuator,
                sessionCoordinator = testSessionCoordinator(InMemorySessionLogStore()),
                logStore = InMemorySessionLogStore(),
            )

        val result = host.tapText("Wi-Fi", exact = true)

        assertTrue(result.ok)
        assertEquals("wifi", actuator.lastTapped?.elementId)
    }

    @Test
    fun `tapText points to inspectScreen when only a visible non actionable match exists`() = runTest {
        val nonActionable =
            element(
                id = "rapido-auto-text",
                ref = "e4",
                label = "Auto ride. Estimated fare ₹242",
                text = "Auto ride. Estimated fare ₹242",
                clickable = false,
                bounds = listOf(40, 1200, 1040, 1360),
                clickabilityReason = "visible_non_actionable",
                tapFallbackEligible = true,
            )
        val host =
            ScriptHost(
                scriptExecutionId = "script-1",
                phoneObserver =
                FakePhoneObserver(
                    listOf(
                        snapshot(
                            elements = emptyList(),
                            visibleElements = listOf(nonActionable),
                        ),
                    ),
                ),
                phoneActuator = FakePhoneActuator(),
                sessionCoordinator = testSessionCoordinator(InMemorySessionLogStore()),
                logStore = InMemorySessionLogStore(),
            )

        val result = host.tapText("Auto ride", exact = false)

        assertFalse(result.ok)
        assertTrue(result.message.contains("inspectScreen"))
        assertTrue(result.message.contains("Visible candidates"))
        assertTrue(result.message.contains("visible_non_actionable"))
    }

    @Test
    fun `inspectScreen returns bounded visible non actionable matches`() = runTest {
        val visibleAuto =
            element(
                id = "rapido-auto-text",
                ref = "e4",
                label = "Auto ride. Estimated fare ₹242",
                text = "Auto ride. Estimated fare ₹242",
                clickable = false,
                bounds = listOf(40, 1200, 1040, 1360),
                clickabilityReason = "visible_non_actionable",
                tapFallbackEligible = true,
            )
        val host =
            ScriptHost(
                scriptExecutionId = "script-1",
                phoneObserver =
                FakePhoneObserver(
                    listOf(
                        snapshot(
                            elements = emptyList(),
                            visibleElements = listOf(
                                element(id = "bike", label = "Bike Direct", text = "Bike Direct"),
                                visibleAuto,
                            ),
                        ),
                    ),
                ),
                phoneActuator = FakePhoneActuator(),
                sessionCoordinator = testSessionCoordinator(InMemorySessionLogStore()),
                logStore = InMemorySessionLogStore(),
            )

        val inspection = host.inspectScreen("""{"text":"Auto ride","limit":5}""")

        assertEquals("Auto ride", inspection.query)
        assertEquals(1, inspection.visibleElements.size)
        assertEquals("rapido-auto-text", inspection.visibleElements.single().id)
        assertEquals(listOf(540, 1280), inspection.visibleElements.single().center)
        assertEquals("visible_non_actionable", inspection.visibleElements.single().clickabilityReason)
        assertTrue(inspection.visibleElements.single().tapFallbackEligible)
    }

    @Test
    fun `tapBounds taps the center point of inspected bounds`() = runTest {
        val actuator = FakePhoneActuator(tapResult = ActionResult.Success("Tapped point."))
        val host =
            ScriptHost(
                scriptExecutionId = "script-1",
                phoneObserver = FakePhoneObserver(listOf(snapshot())),
                phoneActuator = actuator,
                sessionCoordinator = testSessionCoordinator(InMemorySessionLogStore()),
                logStore = InMemorySessionLogStore(),
            )

        val result = host.tapBounds("[40,1200,1040,1360]")

        assertTrue(result.ok)
        assertEquals(540 to 1280, actuator.lastTappedPoint)
        val data = result.data!!.jsonObject
        assertEquals(540, data["x"]!!.jsonPrimitive.int)
        assertEquals(1280, data["y"]!!.jsonPrimitive.int)
    }

    @Test
    fun `tapBounds rejects malformed bounds`() = runTest {
        val actuator = FakePhoneActuator(tapResult = ActionResult.Success("Tapped point."))
        val host =
            ScriptHost(
                scriptExecutionId = "script-1",
                phoneObserver = FakePhoneObserver(listOf(snapshot())),
                phoneActuator = actuator,
                sessionCoordinator = testSessionCoordinator(InMemorySessionLogStore()),
                logStore = InMemorySessionLogStore(),
            )

        val result = host.tapBounds("[40,1200,40,1360]")

        assertFalse(result.ok)
        assertEquals("Invalid bounds. Expected [left, top, right, bottom].", result.message)
        assertEquals(null, actuator.lastTappedPoint)
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
                sessionCoordinator = testSessionCoordinator(InMemorySessionLogStore()),
                logStore = InMemorySessionLogStore(),
            )

        val result = host.typeIntoFocused("apple")

        assertTrue(result.ok)
        assertEquals("search-box", actuator.lastTyped?.first?.elementId)
        assertEquals("apple", actuator.lastTyped?.second)
    }

    @Test
    fun `typeIntoSelector activates a wrapper control and types into the resulting editable element`() = runTest {
        val actuator = FakePhoneActuator(
            tapResult = ActionResult.Success("Tapped wrapper."),
            typeResult = ActionResult.Success("Typed into activated field."),
        )
        val host =
            ScriptHost(
                scriptExecutionId = "script-1",
                phoneObserver =
                FakePhoneObserver(
                    listOf(
                        snapshot(
                            elements =
                            listOf(
                                element(id = "search-wrapper", ref = "e0", label = "Search", role = "control", editable = false),
                            ),
                        ),
                        snapshot(
                            focusedElementId = "search-input",
                            elements =
                            listOf(
                                element(id = "search-wrapper", ref = "e0", label = "Search", role = "control", editable = false),
                                element(
                                    id = "search-input",
                                    ref = "e1",
                                    label = "Search input",
                                    role = "input",
                                    editable = true,
                                    focused = true,
                                ),
                            ),
                        ),
                    ),
                ),
                phoneActuator = actuator,
                sessionCoordinator = testSessionCoordinator(InMemorySessionLogStore()),
                logStore = InMemorySessionLogStore(),
            )

        val result = host.typeIntoSelector("""{"label":"Search"}""", "oranges")

        assertTrue(result.ok)
        assertEquals("search-wrapper", actuator.lastTapped?.elementId)
        assertEquals("search-input", actuator.lastTyped?.first?.elementId)
        assertEquals("oranges", actuator.lastTyped?.second)
    }

    @Test
    fun `focusSelector returns the activated editable element`() = runTest {
        val actuator = FakePhoneActuator(
            tapResult = ActionResult.Success("Tapped wrapper."),
        )
        val host =
            ScriptHost(
                scriptExecutionId = "script-1",
                phoneObserver =
                FakePhoneObserver(
                    listOf(
                        snapshot(
                            elements = listOf(
                                element(id = "search-wrapper", ref = "e0", label = "Search", role = "control", editable = false),
                            ),
                        ),
                        snapshot(
                            focusedElementId = "search-input",
                            elements = listOf(
                                element(id = "search-wrapper", ref = "e0", label = "Search", role = "control", editable = false),
                                element(
                                    id = "search-input",
                                    ref = "e1",
                                    label = "Search input",
                                    role = "input",
                                    editable = true,
                                    focused = true,
                                ),
                            ),
                        ),
                    ),
                ),
                phoneActuator = actuator,
                sessionCoordinator = testSessionCoordinator(InMemorySessionLogStore()),
                logStore = InMemorySessionLogStore(),
            )

        val result = host.focusSelector("""{"label":"Search"}""", timeoutMs = 1000)

        assertTrue(result.ok)
        assertEquals("search-wrapper", actuator.lastTapped?.elementId)
        assertEquals("search-input", result.data!!.jsonObject["activatedElementId"]!!.jsonPrimitive.content)
    }

    @Test
    fun `focusSelector can activate a non clickable wrapper when the actuator succeeds`() = runTest {
        val actuator = FakePhoneActuator(
            tapResult = ActionResult.Success("Tapped wrapper via fallback."),
        )
        val host =
            ScriptHost(
                scriptExecutionId = "script-1",
                phoneObserver =
                FakePhoneObserver(
                    listOf(
                        snapshot(
                            elements = listOf(
                                element(
                                    id = "search-wrapper",
                                    ref = "e0",
                                    label = "Type to search restaurants or dishes",
                                    role = "control",
                                    clickable = false,
                                ),
                            ),
                        ),
                        snapshot(
                            focusedElementId = "search-input",
                            elements = listOf(
                                element(
                                    id = "search-wrapper",
                                    ref = "e0",
                                    label = "Type to search restaurants or dishes",
                                    role = "control",
                                    clickable = false,
                                ),
                                element(
                                    id = "search-input",
                                    ref = "e1",
                                    label = "Search input",
                                    role = "input",
                                    editable = true,
                                    focused = true,
                                ),
                            ),
                        ),
                    ),
                ),
                phoneActuator = actuator,
                sessionCoordinator = testSessionCoordinator(InMemorySessionLogStore()),
                logStore = InMemorySessionLogStore(),
            )

        val result = host.focusSelector("""{"label":"Type to search restaurants or dishes"}""", timeoutMs = 1000)

        assertTrue(result.ok)
        assertEquals("search-wrapper", actuator.lastTapped?.elementId)
        assertEquals("search-input", result.data!!.jsonObject["activatedElementId"]!!.jsonPrimitive.content)
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
                sessionCoordinator = testSessionCoordinator(InMemorySessionLogStore()),
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
                sessionCoordinator = testSessionCoordinator(InMemorySessionLogStore()),
                logStore = InMemorySessionLogStore(),
            )

        val result = host.scrollContainer("el-1", "sideways")

        assertFalse(result.ok)
        assertEquals("Unsupported scroll direction 'sideways'.", result.message)
    }

    @Test
    fun `scrollRef resolves a fresh ref to the underlying element id`() = runTest {
        val actuator = FakePhoneActuator(scrollResult = ActionResult.Success("Scrolled container."))
        val host =
            ScriptHost(
                scriptExecutionId = "script-1",
                phoneObserver =
                FakePhoneObserver(
                    listOf(
                        snapshot(
                            elements = listOf(
                                element(id = "scroll-el", ref = "e1", label = "Product list", scrollable = true),
                            ),
                        ),
                    ),
                ),
                phoneActuator = actuator,
                sessionCoordinator = testSessionCoordinator(InMemorySessionLogStore()),
                logStore = InMemorySessionLogStore(),
            )

        val result = host.scrollRef("e1", "down")

        assertTrue(result.ok)
        assertEquals("scroll-el", actuator.lastScrolled?.first?.elementId)
        assertEquals(ScrollDirection.Down, actuator.lastScrolled?.second)
    }

    @Test
    fun `scrollRef explains when the matched ref is not scrollable`() = runTest {
        val host =
            ScriptHost(
                scriptExecutionId = "script-1",
                phoneObserver =
                FakePhoneObserver(
                    listOf(
                        snapshot(
                            elements = listOf(
                                element(id = "child", ref = "e1", label = "Available networks", scrollable = false),
                            ),
                        ),
                    ),
                ),
                phoneActuator = FakePhoneActuator(),
                sessionCoordinator = testSessionCoordinator(InMemorySessionLogStore()),
                logStore = InMemorySessionLogStore(),
            )

        val result = host.scrollRef("e1", "down")

        assertFalse(result.ok)
        assertTrue(result.message.contains("Use scrollScreen(direction)"))
    }

    @Test
    fun `scrollScreen picks the main scrollable element on the page`() = runTest {
        val actuator = FakePhoneActuator(scrollResult = ActionResult.Success("Scrolled page."))
        val host =
            ScriptHost(
                scriptExecutionId = "script-1",
                phoneObserver =
                FakePhoneObserver(
                    listOf(
                        snapshot(
                            elements = listOf(
                                element(
                                    id = "small-chip-row",
                                    ref = "e0",
                                    label = "Chip row",
                                    scrollable = true,
                                    bounds = listOf(0, 0, 100, 40),
                                ),
                                element(
                                    id = "main-list",
                                    ref = "e1",
                                    label = "Settings list",
                                    scrollable = true,
                                    bounds = listOf(0, 0, 100, 400),
                                ),
                            ),
                        ),
                    ),
                ),
                phoneActuator = actuator,
                sessionCoordinator = testSessionCoordinator(InMemorySessionLogStore()),
                logStore = InMemorySessionLogStore(),
            )

        val result = host.scrollScreen("down")

        assertTrue(result.ok)
        assertEquals("main-list", actuator.lastScrolled?.first?.elementId)
        assertEquals(ScrollDirection.Down, actuator.lastScrolled?.second)
    }

    private fun snapshot(
        packageName: String = "com.example.app",
        elements: List<UiElement> = listOf(element()),
        visibleElements: List<UiElement> = elements,
        focusedElementId: String? = null,
        windowCandidates: List<WindowCandidate> = emptyList(),
        selectedWindowReason: String? = null,
    ): UiSnapshot = UiSnapshot(
        snapshotId = "snapshot",
        capturedAt = Instant.parse("2026-04-16T00:00:00Z"),
        foregroundPackage = packageName,
        visibleText = listOf("Alpha"),
        actionableElements = elements,
        visibleElements = visibleElements,
        focusedElementId = focusedElementId,
        windowCandidates = windowCandidates,
        selectedWindowReason = selectedWindowReason,
    )

    private fun element(
        id: String = "el-1",
        ref: String = id,
        label: String = "Action",
        text: String? = null,
        role: String = "button",
        clickable: Boolean = true,
        focusable: Boolean = false,
        editable: Boolean = false,
        focused: Boolean = false,
        scrollable: Boolean = false,
        bounds: List<Int> = listOf(0, 0, 100, 100),
        actions: List<String> = emptyList(),
        tapFallbackEligible: Boolean = false,
        clickabilityReason: String = "",
    ): UiElement = UiElement(
        id = id,
        ref = ref,
        role = role,
        label = label,
        text = text,
        clickable = clickable,
        focusable = focusable,
        editable = editable,
        focused = focused,
        scrollable = scrollable,
        bounds = bounds,
        actions = actions,
        tapFallbackEligible = tapFallbackEligible,
        clickabilityReason = clickabilityReason,
    )
}

private fun testSessionCoordinator(logStore: InMemorySessionLogStore): SessionCoordinator {
    val root = Files.createTempDirectory("claune-script-host").toFile()
    val store = CodingSessionStore(cwd = root.absolutePath, agentDir = root.resolve("agent"))
    return SessionCoordinator(logStore, store)
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
    var lastTappedPoint: Pair<Int, Int>? = null
    var lastTyped: Pair<ElementRef, String>? = null
    var lastScrolled: Pair<ElementRef, ScrollDirection>? = null

    override suspend fun tap(target: ElementRef): ActionResult {
        lastTapped = target
        return tapResult
    }

    override suspend fun tapPoint(x: Int, y: Int): ActionResult {
        lastTappedPoint = x to y
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

private class FakeInstalledAppRegistry(private val apps: List<InstalledAppPayload> = emptyList()) : InstalledAppRegistry {
    var launchedPackage: String? = null

    override fun listLaunchableApps(): List<InstalledAppPayload> = apps

    override fun launchPackage(packageName: String): HostCallOutcome {
        launchedPackage = packageName
        return HostCallOutcome(ok = true, message = "Launched package '$packageName'.")
    }
}
