package com.divyanshgolyan.claune.android.scripting

import com.divyanshgolyan.claune.android.data.local.CodingSessionStore
import com.divyanshgolyan.claune.android.data.local.InMemorySessionLogStore
import com.divyanshgolyan.claune.android.runtime.ActionResult
import com.divyanshgolyan.claune.android.runtime.ElementRef
import com.divyanshgolyan.claune.android.runtime.PhoneActuator
import com.divyanshgolyan.claune.android.runtime.PhoneObserver
import com.divyanshgolyan.claune.android.runtime.ScreenNode
import com.divyanshgolyan.claune.android.runtime.ScreenState
import com.divyanshgolyan.claune.android.runtime.ScreenWindow
import com.divyanshgolyan.claune.android.runtime.ScrollDirection
import com.divyanshgolyan.claune.android.runtime.SessionCoordinator
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
    fun `observeScreen returns screen observation payload and records screen state`() = runTest {
        val logStore = InMemorySessionLogStore()
        val coordinator = testSessionCoordinator(logStore)
        val snapshot = snapshot(
            packageName = "com.android.settings",
            elements = listOf(element(id = "el-1", label = "Wi-Fi")),
        )
        val host =
            ScriptHost(
                scriptExecutionId = "script-1",
                phoneObserver = FakePhoneObserver(listOf(snapshot)),
                phoneActuator = FakePhoneActuator(),
                sessionCoordinator = coordinator,
                logStore = logStore,
            )

        val payload = host.observeScreen("{}")

        assertEquals("com.android.settings", payload.foregroundPackage)
        assertEquals("snapshot", payload.currentSnapshotId)
        assertEquals("interactions", payload.mode)
        assertTrue(payload.summaryText.orEmpty().contains("Wi-Fi"))
        assertEquals("Wi-Fi", payload.elements.single().normalizedLabel)
        assertEquals(1, logStore.recentScreenStates().size)
    }

    @Test
    fun `interaction summary includes visible labels even when they are not actions`() = runTest {
        val host =
            ScriptHost(
                scriptExecutionId = "script-1",
                phoneObserver =
                FakePhoneObserver(
                    listOf(
                        snapshot(
                            packageName = "in.swiggy.android",
                            elements =
                            listOf(
                                element(id = "food", label = "Food", clickable = true, bounds = listOf(22, 316, 259, 510)),
                                element(
                                    id = "instamart-label",
                                    label = "Instamart",
                                    role = "control",
                                    clickable = false,
                                    bounds = listOf(259, 447, 496, 494),
                                ),
                                element(
                                    id = "search",
                                    label = "Search for products",
                                    clickable = true,
                                    bounds = listOf(44, 557, 1036, 694),
                                ),
                            ),
                        ),
                    ),
                ),
                phoneActuator = FakePhoneActuator(),
                sessionCoordinator = testSessionCoordinator(InMemorySessionLogStore()),
                logStore = InMemorySessionLogStore(),
            )

        val payload = host.observeScreen("{}")

        assertTrue(payload.summaryText.orEmpty().contains("element instamart-label control \"Instamart\""))
        assertTrue(payload.summaryText.orEmpty().contains("element search button \"Search for products\""))
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

        assertTrue(result.message, result.ok)
        assertEquals("Matched package condition for 'com.two.app'.", result.message)
    }

    @Test
    fun `waitForState matches regex package literals`() = runTest {
        var currentTime = Instant.parse("2026-04-16T00:00:00Z")
        val host =
            ScriptHost(
                scriptExecutionId = "script-1",
                phoneObserver = FakePhoneObserver(listOf(snapshot(packageName = "com.mi.android.globallauncher"))),
                phoneActuator = FakePhoneActuator(),
                sessionCoordinator = testSessionCoordinator(InMemorySessionLogStore()),
                logStore = InMemorySessionLogStore(),
                now = { currentTime },
                sleeper = { delayMs -> currentTime = currentTime.plusMillis(delayMs) },
            )

        val result = host.waitForState(type = "package", value = "/launcher|home|global/", timeoutMs = 300)

        assertTrue(result.message, result.ok)
    }

    @Test
    fun `waitForState treats simple alternation strings as text regexes`() = runTest {
        var currentTime = Instant.parse("2026-04-16T00:00:00Z")
        val host =
            ScriptHost(
                scriptExecutionId = "script-1",
                phoneObserver =
                FakePhoneObserver(
                    listOf(
                        snapshot(
                            elements = listOf(element(id = "auto", label = "Auto ride", text = "Auto ride")),
                        ),
                    ),
                ),
                phoneActuator = FakePhoneActuator(),
                sessionCoordinator = testSessionCoordinator(InMemorySessionLogStore()),
                logStore = InMemorySessionLogStore(),
                now = { currentTime },
                sleeper = { delayMs -> currentTime = currentTime.plusMillis(delayMs) },
            )

        val result = host.waitForState(type = "text", value = "Rapido|Auto|Bike", timeoutMs = 300)

        assertTrue(result.ok)
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
                            windows =
                            listOf(
                                ScreenWindow(
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
    fun `tapText can choose first duplicate text match explicitly`() = runTest {
        val actuator = FakePhoneActuator(tapResult = ActionResult.Success("Tapped first duplicate."))
        val host =
            ScriptHost(
                scriptExecutionId = "script-1",
                phoneObserver =
                FakePhoneObserver(
                    listOf(
                        snapshot(
                            elements =
                            listOf(
                                element(id = "wrapper", ref = "e0", label = "Where are you going?", text = "Where are you going?"),
                                element(id = "button", ref = "e1", label = "Where are you going?", text = "Where are you going?"),
                            ),
                        ),
                    ),
                ),
                phoneActuator = actuator,
                sessionCoordinator = testSessionCoordinator(InMemorySessionLogStore()),
                logStore = InMemorySessionLogStore(),
            )

        val result = host.tapText("Where are you going?", exact = true, first = true)

        assertTrue(result.ok)
        assertEquals("wrapper", actuator.lastTapped?.elementId)
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
        assertEquals("rapido-auto-text", inspection.visibleElements.single().elementId)
        assertEquals(listOf(540, 1280), inspection.visibleElements.single().center)
        assertEquals("visible_non_actionable", inspection.visibleElements.single().clickabilityReason)
        assertTrue(inspection.visibleElements.single().tapFallbackEligible)
    }

    @Test
    fun `observeScreen returns visible interaction groups and deduplicated actions`() = runTest {
        val label =
            element(
                id = "pear-label",
                ref = "pear-label",
                label = "Packham Pear - South Africa",
                clickable = false,
                bounds = listOf(40, 320, 640, 390),
            ).copy(path = listOf(0, 0))
        val addText =
            element(
                id = "pear-add-text",
                ref = "pear-add-text",
                label = "ADD",
                clickable = true,
                bounds = listOf(880, 330, 1020, 410),
            ).copy(path = listOf(0, 1, 0))
        val addContainer =
            element(
                id = "pear-add",
                ref = "pear-add",
                label = "ADD",
                clickable = true,
                bounds = listOf(870, 320, 1030, 420),
                children = listOf(addText),
            ).copy(path = listOf(0, 1))
        val row =
            element(
                id = "pear-row",
                ref = "pear-row",
                label = "Packham Pear row",
                clickable = false,
                bounds = listOf(20, 300, 1060, 450),
                children = listOf(label, addContainer),
            )
        val host =
            ScriptHost(
                scriptExecutionId = "script-1",
                phoneObserver = FakePhoneObserver(listOf(snapshot(elements = emptyList(), visibleElements = listOf(row)))),
                phoneActuator = FakePhoneActuator(),
                sessionCoordinator = testSessionCoordinator(InMemorySessionLogStore()),
                logStore = InMemorySessionLogStore(),
            )

        val payload = host.observeScreen("{}")

        assertEquals("interactions", payload.mode)
        assertTrue(payload.elements.any { it.normalizedLabel == "Packham Pear - South Africa" })
        assertEquals(1, payload.actions.count { it.label == "ADD" })
        val addAction = payload.actions.single { it.label == "ADD" }
        assertTrue(addAction.equivalentRefs.contains("pear-add"))
        assertTrue(addAction.equivalentRefs.contains("pear-add-text"))
        val group = payload.groups.single()
        assertTrue(group.labelSummary.contains("Packham Pear"))
        assertTrue(group.actionIds.contains(addAction.id))
    }

    @Test
    fun `observeScreen keeps repeated actions distinct with stable ids`() = runTest {
        fun row(index: Int, name: String, top: Int): ScreenNode {
            val label = element(
                id = "item-$index-label",
                ref = "item-$index-label",
                label = name,
                clickable = false,
                bounds = listOf(40, top, 640, top + 70),
            ).copy(path = listOf(index, 0))
            val add = element(
                id = "item-$index-add",
                ref = "item-$index-add",
                label = "ADD",
                clickable = true,
                bounds = listOf(880, top, 1020, top + 80),
            ).copy(path = listOf(index, 1))
            return element(
                id = "item-$index-row",
                ref = "item-$index-row",
                label = "$name row",
                clickable = false,
                bounds = listOf(20, top - 20, 1060, top + 100),
                children = listOf(label, add),
            ).copy(path = listOf(index))
        }

        val screen = snapshot(
            elements = emptyList(),
            visibleElements = listOf(
                row(index = 0, name = "Packham Pear", top = 320),
                row(index = 1, name = "Banana", top = 520),
            ),
        )
        val actuator = FakePhoneActuator(tapResult = ActionResult.Success("Tapped repeated action."))
        val host =
            ScriptHost(
                scriptExecutionId = "script-1",
                phoneObserver = FakePhoneObserver(listOf(screen)),
                phoneActuator = actuator,
                sessionCoordinator = testSessionCoordinator(InMemorySessionLogStore()),
                logStore = InMemorySessionLogStore(),
            )

        val actions = host.observeScreen("{}").actions.filter { it.label == "ADD" }

        assertEquals(2, actions.size)
        assertEquals(2, actions.map { it.id }.distinct().size)
        assertTrue(actions.all { it.id.startsWith("a_click_") })

        val bananaAction = actions.single { it.targetElementId == "item-1-add" }
        val result = host.performAction(bananaAction.id)

        assertTrue(result.ok)
        assertEquals("item-1-add", actuator.lastTapped?.elementId)
    }

    @Test
    fun `performAction revalidates current interaction action and taps its target`() = runTest {
        val add =
            element(
                id = "pear-add",
                ref = "pear-add",
                label = "ADD",
                clickable = true,
                bounds = listOf(870, 320, 1030, 420),
            )
        val actuator = FakePhoneActuator(tapResult = ActionResult.Success("Tapped ADD."))
        val host =
            ScriptHost(
                scriptExecutionId = "script-1",
                phoneObserver = FakePhoneObserver(listOf(snapshot(elements = listOf(add)))),
                phoneActuator = actuator,
                sessionCoordinator = testSessionCoordinator(InMemorySessionLogStore()),
                logStore = InMemorySessionLogStore(),
            )
        val actionId = snapshot(elements = listOf(add)).toInteractionObservationPayload().actions.single().id

        val result = host.performAction(actionId)

        assertTrue(result.ok)
        assertEquals("pear-add", actuator.lastTapped?.elementId)
    }

    @Test
    fun `inspectScreen honors requested limit and ranks actionable leaves before large ancestors`() = runTest {
        val rootContainer =
            element(
                id = "bottom-sheet",
                ref = "sheet",
                label = "Bottom Sheet",
                role = "control",
                clickable = false,
                bounds = listOf(0, 0, 1080, 2400),
            )
        val usefulButton =
            element(
                id = "book",
                ref = "book",
                label = "Book ride",
                text = "Book ride",
                bounds = listOf(44, 2100, 1036, 2228),
                actions = listOf("ACTION_CLICK"),
            )
        val filler = (1..25).map { index ->
            element(
                id = "filler-$index",
                ref = "filler-$index",
                label = "Filler $index",
                clickable = false,
                bounds = listOf(0, 100 + index, 1080, 2400),
            )
        }
        val host =
            ScriptHost(
                scriptExecutionId = "script-1",
                phoneObserver =
                FakePhoneObserver(
                    listOf(
                        snapshot(
                            elements = emptyList(),
                            visibleElements = listOf(rootContainer, usefulButton) + filler,
                        ),
                    ),
                ),
                phoneActuator = FakePhoneActuator(),
                sessionCoordinator = testSessionCoordinator(InMemorySessionLogStore()),
                logStore = InMemorySessionLogStore(),
            )

        val inspection = host.inspectScreen("""{"limit":25}""")

        assertEquals(25, inspection.visibleElements.size)
        assertEquals("book", inspection.visibleElements.first().elementId)
    }

    @Test
    fun `findRawNodes searches latest screen and returns nearest actionable target`() = runTest {
        val reasonText =
            element(
                id = "reason-text",
                ref = "reason-text",
                label = "Driver not getting closer",
                text = "Driver not getting closer",
                clickable = false,
                bounds = listOf(80, 1420, 900, 1480),
            ).copy(path = listOf(0, 0))
        val reasonRow =
            element(
                id = "reason-row",
                ref = "reason-row",
                label = "Cancellation reason row",
                role = "button",
                clickable = true,
                bounds = listOf(0, 1397, 1080, 1529),
                children = listOf(reasonText),
            )
        val host =
            ScriptHost(
                scriptExecutionId = "script-1",
                phoneObserver = FakePhoneObserver(listOf(snapshot(elements = emptyList(), visibleElements = listOf(reasonRow)))),
                phoneActuator = FakePhoneActuator(),
                sessionCoordinator = testSessionCoordinator(InMemorySessionLogStore()),
                logStore = InMemorySessionLogStore(),
            )

        val result = host.findRawNodes("""{"pattern":"driver.*closer","limit":5}""")

        assertEquals("snapshot", result.snapshotId)
        assertEquals(null, result.error)
        assertEquals(1, result.matches.size)
        assertEquals("reason-text", result.matches.single().node.elementId)
        assertEquals(listOf("label", "text"), result.matches.single().matchedFields)
        assertEquals("reason-row", result.matches.single().nearestActionable?.elementId)
        assertTrue(result.matches.single().ancestorLabels.contains("Cancellation reason row"))
    }

    @Test
    fun `findRawNodes returns bounded error for invalid regex`() = runTest {
        val host =
            ScriptHost(
                scriptExecutionId = "script-1",
                phoneObserver = FakePhoneObserver(listOf(snapshot())),
                phoneActuator = FakePhoneActuator(),
                sessionCoordinator = testSessionCoordinator(InMemorySessionLogStore()),
                logStore = InMemorySessionLogStore(),
            )

        val result = host.findRawNodes("""{"pattern":"["}""")

        assertTrue(result.error.orEmpty().contains("invalid regex"))
        assertTrue(result.matches.isEmpty())
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
        assertEquals("apple", actuator.lastTypedFocused)
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
        assertEquals("oranges", actuator.lastTypedFocused)
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
        elements: List<ScreenNode> = listOf(element()),
        visibleElements: List<ScreenNode> = elements,
        focusedElementId: String? = null,
        windows: List<ScreenWindow> = emptyList(),
        selectedWindowReason: String? = null,
    ): ScreenState {
        val children = (visibleElements + elements)
            .distinctBy { it.elementId }
            .mapIndexed { index, node ->
                node.copy(
                    path = listOf(index),
                    focused = node.focused || node.elementId == focusedElementId,
                )
            }
        val root = element(
            id = "root",
            ref = "root",
            label = "Root",
            role = "root",
            clickable = false,
            bounds = listOf(0, 0, 1080, 2400),
            children = children,
            visibleToUser = true,
        )
        return ScreenState(
            snapshotId = "snapshot",
            capturedAt = Instant.parse("2026-04-16T00:00:00Z").toString(),
            foregroundPackage = packageName,
            root = root,
            windows = windows,
            selectedWindowReason = selectedWindowReason,
        )
    }

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
        children: List<ScreenNode> = emptyList(),
        visibleToUser: Boolean = true,
    ): ScreenNode = ScreenNode(
        path = emptyList(),
        ref = ref,
        elementId = id,
        role = role,
        label = label,
        text = text,
        visibleToUser = visibleToUser,
        clickable = clickable,
        focusable = focusable,
        editable = editable,
        focused = focused,
        scrollable = scrollable,
        bounds = bounds,
        actions = actions,
        tapFallbackEligible = tapFallbackEligible,
        clickabilityReason = clickabilityReason,
        children = children,
    )
}

private fun testSessionCoordinator(logStore: InMemorySessionLogStore): SessionCoordinator {
    val root = Files.createTempDirectory("claune-script-host").toFile()
    val store = CodingSessionStore(cwd = root.absolutePath, agentDir = root.resolve("agent"))
    return SessionCoordinator(logStore, store)
}

private class FakePhoneObserver(private val snapshots: List<ScreenState>) : PhoneObserver {
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

private class FakePhoneActuator(
    private val tapResult: ActionResult = ActionResult.Blocked("No tap configured."),
    private val typeResult: ActionResult = ActionResult.Blocked("No typing configured."),
    private val scrollResult: ActionResult = ActionResult.Blocked("No scroll configured."),
) : PhoneActuator {
    var lastTapped: ElementRef? = null
    var lastTappedPoint: Pair<Int, Int>? = null
    var lastTyped: Pair<ElementRef, String>? = null
    var lastTypedFocused: String? = null
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

    override suspend fun typeFocused(text: String): ActionResult {
        lastTypedFocused = text
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
