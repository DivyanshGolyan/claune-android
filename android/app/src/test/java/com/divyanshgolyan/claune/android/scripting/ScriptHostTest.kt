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
import kotlinx.serialization.json.jsonArray
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
                phoneObserver = FakePhoneObserver(
                    listOf(
                        snapshot(packageName = "com.mi.android.globallauncher"),
                        snapshot(packageName = "com.dreamplug.androidapp"),
                    ),
                ),
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
    fun `launchApp returns immediately when target package is already foreground`() = runTest {
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
        assertEquals("Package 'com.dreamplug.androidapp' is already foreground.", result.message)
        assertEquals(null, registry.launchedPackage)
        val tags = result.data!!.jsonObject["traceTags"]!!.jsonArray.map { it.jsonPrimitive.content }
        assertTrue(tags.contains("launch_already_foreground"))
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
    fun `locatorQuery returns strict candidate count and evidence`() = runTest {
        val host =
            ScriptHost(
                scriptExecutionId = "script-1",
                phoneObserver =
                FakePhoneObserver(
                    listOf(
                        snapshot(
                            elements =
                            listOf(
                                element(id = "wifi-1", ref = "e1", label = "Wi-Fi", text = "Wi-Fi"),
                                element(id = "wifi-2", ref = "e2", label = "Wi-Fi details", text = "Wi-Fi details"),
                            ),
                        ),
                    ),
                ),
                phoneActuator = FakePhoneActuator(),
                sessionCoordinator = testSessionCoordinator(InMemorySessionLogStore()),
                logStore = InMemorySessionLogStore(),
            )

        val result = host.locatorQuery("""{"kind":"text","text":"Wi-Fi"}""")

        assertTrue(result.ok)
        assertEquals(2, result.data!!.jsonObject["count"]!!.jsonPrimitive.int)
        assertEquals(
            "wifi-1",
            result.data
                .jsonObject["candidates"]!!
                .jsonArray
                .first()
                .jsonObject["elementId"]!!
                .jsonPrimitive
                .content,
        )
    }

    @Test
    fun `role locator regex name filters accessible names`() = runTest {
        val host =
            ScriptHost(
                scriptExecutionId = "script-1",
                phoneObserver =
                FakePhoneObserver(
                    listOf(
                        snapshot(
                            elements =
                            listOf(
                                element(id = "add", ref = "e1", label = "Add", role = "button", clickable = true),
                                element(id = "remove", ref = "e2", label = "Remove", role = "button", clickable = true),
                            ),
                        ),
                    ),
                ),
                phoneActuator = FakePhoneActuator(),
                sessionCoordinator = testSessionCoordinator(InMemorySessionLogStore()),
                logStore = InMemorySessionLogStore(),
            )

        val result = host.locatorQuery("""{"kind":"role","role":"button","pattern":"^Add$","flags":""}""")

        assertTrue(result.ok)
        assertEquals(1, result.data!!.jsonObject["count"]!!.jsonPrimitive.int)
        assertEquals(
            "add",
            result.data
                .jsonObject["candidates"]!!
                .jsonArray
                .single()
                .jsonObject["elementId"]!!
                .jsonPrimitive
                .content,
        )
    }

    @Test
    fun `locatorClick is strict and reports ambiguity`() = runTest {
        val host =
            ScriptHost(
                scriptExecutionId = "script-1",
                phoneObserver =
                FakePhoneObserver(
                    listOf(
                        snapshot(
                            elements =
                            listOf(
                                element(id = "add-1", ref = "e1", label = "Add", text = "Add"),
                                element(id = "add-2", ref = "e2", label = "Add", text = "Add"),
                            ),
                        ),
                    ),
                ),
                phoneActuator = FakePhoneActuator(tapResult = ActionResult.Success("Tapped.")),
                sessionCoordinator = testSessionCoordinator(InMemorySessionLogStore()),
                logStore = InMemorySessionLogStore(),
            )

        val result = host.locatorClick("""{"kind":"text","text":"Add"}""", """{"timeoutMs":0}""")

        assertFalse(result.ok)
        assertEquals("ambiguous_match", result.errorCode)
    }

    @Test
    fun `locatorClick reports stable ambiguity without polling`() = runTest {
        var currentTime = Instant.parse("2026-04-16T00:00:00Z")
        val logStore = InMemorySessionLogStore()
        val host =
            ScriptHost(
                scriptExecutionId = "script-1",
                phoneObserver =
                FakePhoneObserver(
                    listOf(
                        snapshot(
                            elements =
                            listOf(
                                element(id = "add-1", ref = "e1", label = "Add", text = "Add"),
                                element(id = "add-2", ref = "e2", label = "Add", text = "Add"),
                            ),
                        ),
                    ),
                ),
                phoneActuator = FakePhoneActuator(tapResult = ActionResult.Success("Tapped.")),
                sessionCoordinator = testSessionCoordinator(logStore),
                logStore = logStore,
                now = { currentTime },
                sleeper = { delayMs -> currentTime = currentTime.plusMillis(delayMs) },
            )

        val result = host.locatorClick("""{"kind":"text","text":"Add"}""", "{}")

        assertFalse(result.ok)
        assertEquals("ambiguous_match", result.errorCode)
        assertEquals(1, logStore.recentScreenStates().size)
        assertEquals(Instant.parse("2026-04-16T00:00:00Z"), currentTime)
    }

    @Test
    fun `locatorClick taps unique actionable target`() = runTest {
        val actuator = FakePhoneActuator(tapResult = ActionResult.Success("Tapped."))
        val host =
            ScriptHost(
                scriptExecutionId = "script-1",
                phoneObserver =
                FakePhoneObserver(
                    listOf(
                        snapshot(
                            elements =
                            listOf(
                                element(id = "add", ref = "e1", label = "Add", text = "Add"),
                            ),
                        ),
                    ),
                ),
                phoneActuator = actuator,
                sessionCoordinator = testSessionCoordinator(InMemorySessionLogStore()),
                logStore = InMemorySessionLogStore(),
            )

        val result = host.locatorClick("""{"kind":"text","text":"Add"}""", "{}")

        assertTrue(result.ok)
        assertEquals("add", actuator.lastTapped?.elementId)
    }

    @Test
    fun `locator resolver dedupes wrapper and text child to activation target`() = runTest {
        val actuator = FakePhoneActuator(tapResult = ActionResult.Success("Tapped."))
        val textChild = element(
            id = "instamart-text",
            ref = "e1_0",
            label = "Instamart",
            text = "Instamart",
            clickable = false,
            path = listOf(0, 0),
        )
        val wrapper = element(
            id = "instamart-wrapper",
            ref = "e1",
            label = "Instamart",
            text = null,
            clickable = true,
            children = listOf(textChild),
        )
        val host =
            ScriptHost(
                scriptExecutionId = "script-1",
                phoneObserver = FakePhoneObserver(listOf(snapshot(elements = listOf(wrapper)))),
                phoneActuator = actuator,
                sessionCoordinator = testSessionCoordinator(InMemorySessionLogStore()),
                logStore = InMemorySessionLogStore(),
            )

        val query = host.locatorQuery("""{"kind":"text","text":"Instamart"}""")
        val click = host.locatorClick("""{"kind":"text","text":"Instamart"}""", """{"timeoutMs":0}""")

        assertTrue(query.ok)
        assertEquals(1, query.data!!.jsonObject["count"]!!.jsonPrimitive.int)
        assertTrue(click.ok)
        assertEquals("instamart-wrapper", actuator.lastTapped?.elementId)
    }

    @Test
    fun `locator click prefers matched actionable child before actionable ancestor`() = runTest {
        val actuator = FakePhoneActuator(tapResult = ActionResult.Success("Tapped."))
        val searchChild = element(
            id = "search-child",
            ref = "e1_0",
            label = "Search",
            text = "Search",
            clickable = true,
            path = listOf(0, 0),
        )
        val wrapper = element(
            id = "search-wrapper",
            ref = "e1",
            label = "Header wrapper",
            clickable = true,
            children = listOf(searchChild),
        )
        val host =
            ScriptHost(
                scriptExecutionId = "script-1",
                phoneObserver = FakePhoneObserver(listOf(snapshot(elements = listOf(wrapper)))),
                phoneActuator = actuator,
                sessionCoordinator = testSessionCoordinator(InMemorySessionLogStore()),
                logStore = InMemorySessionLogStore(),
            )

        val click = host.locatorClick("""{"kind":"text","text":"Search"}""", """{"timeoutMs":0}""")

        assertTrue(click.ok)
        assertEquals("search-child", actuator.lastTapped?.elementId)
    }

    @Test
    fun `wildcard locator describes visible candidates without debug inspection`() = runTest {
        val host =
            ScriptHost(
                scriptExecutionId = "script-1",
                phoneObserver =
                FakePhoneObserver(
                    listOf(
                        snapshot(
                            elements = listOf(
                                element(id = "search", ref = "e1", label = "Search", resourceId = "app:id/search"),
                                element(id = "add", ref = "e2", label = "Add", text = "Add"),
                            ),
                        ),
                    ),
                ),
                phoneActuator = FakePhoneActuator(),
                sessionCoordinator = testSessionCoordinator(InMemorySessionLogStore()),
                logStore = InMemorySessionLogStore(),
            )

        val count = host.locatorCount("""{"kind":"all"}""")
        val describe = host.locatorDescribe("""{"kind":"all"}""", """{"limit":1}""")

        assertTrue(count.ok)
        assertEquals(2, count.data!!.jsonObject["count"]!!.jsonPrimitive.int)
        assertTrue(describe.ok)
        val describeData = describe.data!!.jsonObject
        assertEquals(2, describeData["count"]!!.jsonPrimitive.int)
        assertTrue(describeData["truncated"]!!.jsonPrimitive.content.toBoolean())
        assertEquals("supported_discovery", describeData["traceTags"]!!.jsonArray.first().jsonPrimitive.content)
    }

    @Test
    fun `locator visibility helpers return booleans without strict action assertions`() = runTest {
        val host =
            ScriptHost(
                scriptExecutionId = "script-1",
                phoneObserver = FakePhoneObserver(listOf(snapshot(elements = listOf(element(id = "done", label = "Done"))))),
                phoneActuator = FakePhoneActuator(),
                sessionCoordinator = testSessionCoordinator(InMemorySessionLogStore()),
                logStore = InMemorySessionLogStore(),
            )

        val visible = host.locatorIsVisible("""{"kind":"text","text":"Done"}""")
        val hidden = host.locatorIsHidden("""{"kind":"text","text":"Missing"}""")

        assertTrue(visible.ok)
        assertTrue(visible.data!!.jsonObject["visible"]!!.jsonPrimitive.content.toBoolean())
        assertTrue(hidden.ok)
        assertTrue(hidden.data!!.jsonObject["hidden"]!!.jsonPrimitive.content.toBoolean())
    }

    @Test
    fun `placeholder filter extraction and enter press use supported locator APIs`() = runTest {
        val actuator = FakePhoneActuator(
            tapResult = ActionResult.Success("Tapped."),
            pressEnterResult = ActionResult.Success("Pressed Enter."),
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
                                    id = "search",
                                    ref = "e1",
                                    label = "Search products",
                                    text = "Search products",
                                    role = "input",
                                    editable = true,
                                    focused = true,
                                    clickable = true,
                                ),
                                element(
                                    id = "row-1",
                                    ref = "e2",
                                    label = "Apple Royal Gala ₹180",
                                    text = "Apple Royal Gala ₹180",
                                    role = "listitem",
                                    clickable = false,
                                ),
                                element(
                                    id = "row-2",
                                    ref = "e3",
                                    label = "Banana ₹60",
                                    text = "Banana ₹60",
                                    role = "listitem",
                                    clickable = false,
                                ),
                            ),
                        ),
                    ),
                ),
                phoneActuator = actuator,
                sessionCoordinator = testSessionCoordinator(InMemorySessionLogStore()),
                logStore = InMemorySessionLogStore(),
            )

        val placeholder = host.locatorQuery("""{"kind":"placeholder","text":"Search"}""")
        val filteredText =
            host.locatorTextContent(
                """{"kind":"role","role":"listitem","filters":[{"hasText":"Apple"}]}""",
                """{"timeoutMs":0}""",
            )
        val allTexts = host.locatorAllTextContents("""{"kind":"role","role":"listitem"}""")
        val press = host.locatorPress("""{"kind":"placeholder","text":"Search"}""", "Enter", """{"timeoutMs":0}""")

        assertEquals(1, placeholder.data!!.jsonObject["count"]!!.jsonPrimitive.int)
        assertTrue(filteredText.ok)
        assertEquals("Apple Royal Gala ₹180", filteredText.data!!.jsonObject["text"]!!.jsonPrimitive.content)
        assertTrue(allTexts.data!!.jsonObject["texts"]!!.jsonArray.map { it.jsonPrimitive.content }.contains("Banana ₹60"))
        assertTrue(press.ok)
        assertEquals("search", actuator.lastPressedEnter?.elementId)
    }

    @Test
    fun `locator text extraction includes descendant text`() = runTest {
        val card = element(
            id = "card",
            ref = "e1",
            label = "Product card",
            role = "listitem",
            clickable = true,
            children = listOf(
                element(id = "title", ref = "e1_0", label = "Daily Apple", text = "Daily Apple", role = "control"),
                element(id = "price", ref = "e1_1", label = "₹101", text = "₹101", role = "control"),
            ),
        )
        val host =
            ScriptHost(
                scriptExecutionId = "script-1",
                phoneObserver = FakePhoneObserver(listOf(snapshot(elements = listOf(card)))),
                phoneActuator = FakePhoneActuator(),
                sessionCoordinator = testSessionCoordinator(InMemorySessionLogStore()),
                logStore = InMemorySessionLogStore(),
            )

        val single = host.locatorTextContent("""{"kind":"role","role":"listitem"}""", """{"timeoutMs":0}""")
        val all = host.locatorAllTextContents("""{"kind":"role","role":"listitem"}""")

        assertTrue(single.ok)
        assertEquals("Product card | Daily Apple | ₹101", single.data!!.jsonObject["text"]!!.jsonPrimitive.content)
        assertEquals(
            "Product card | Daily Apple | ₹101",
            all.data!!.jsonObject["texts"]!!.jsonArray.single().jsonPrimitive.content,
        )
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
        assertTrue(actions.any { it.targetElementId == "item-1-add" })
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
    fun `findRawNodes does not search resource ids unless requested`() = runTest {
        val host =
            ScriptHost(
                scriptExecutionId = "script-1",
                phoneObserver =
                FakePhoneObserver(
                    listOf(
                        snapshot(
                            elements = listOf(
                                element(
                                    id = "search",
                                    ref = "e1",
                                    label = "Search",
                                    resourceId = "com.example:id/special_search_box",
                                ),
                            ),
                        ),
                    ),
                ),
                phoneActuator = FakePhoneActuator(),
                sessionCoordinator = testSessionCoordinator(InMemorySessionLogStore()),
                logStore = InMemorySessionLogStore(),
            )

        val defaultResult = host.findRawNodes("""{"pattern":"special_search_box"}""")
        val explicitResult = host.findRawNodes("""{"pattern":"special_search_box","fields":["resourceId"]}""")

        assertTrue(defaultResult.matches.isEmpty())
        assertEquals(1, explicitResult.matches.size)
        assertEquals(listOf("resourceId"), explicitResult.matches.single().matchedFields)
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
    fun `locatorFill reuses wrapper activation text entry`() = runTest {
        val actuator = FakePhoneActuator(
            tapResult = ActionResult.Success("Tapped wrapper."),
            typeResult = ActionResult.Success("Set text."),
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
                                    text = "apple",
                                ),
                            ),
                        ),
                    ),
                ),
                phoneActuator = actuator,
                sessionCoordinator = testSessionCoordinator(InMemorySessionLogStore()),
                logStore = InMemorySessionLogStore(),
            )

        val result = host.locatorFill("""{"kind":"label","text":"Search"}""", "apple", """{"timeoutMs":1000}""")

        assertTrue(result.ok)
        assertEquals("search-wrapper", actuator.lastTapped?.elementId)
        assertEquals("search-input" to "apple", actuator.lastTyped?.let { it.first.elementId to it.second })
        val data = result.data!!.jsonObject
        assertEquals("activate_then_set_text", data["method"]!!.jsonPrimitive.content)
        assertTrue(data["textObservedAfter"]!!.jsonPrimitive.content.toBoolean())
        assertEquals("fill_activation", data["traceTags"]!!.jsonArray.first().jsonPrimitive.content)
        assertTrue(data["editableCandidates"]!!.jsonArray.isNotEmpty())
    }

    @Test
    fun `locatorFill collapses nested search surface candidates`() = runTest {
        val actuator = FakePhoneActuator(
            tapResult = ActionResult.Success("Tapped search."),
            typeResult = ActionResult.Success("Set text."),
        )
        val searchFrame = element(
            id = "search-frame",
            ref = "e0_0",
            label = "Search for atta, dal, coke and more",
            role = "control",
            clickable = true,
            bounds = listOf(0, 90, 1080, 290),
        )
        val broadWrapper = element(
            id = "broad-wrapper",
            ref = "e0",
            label = "Search for atta, dal, coke and more",
            role = "control",
            clickable = false,
            tapFallbackEligible = true,
            bounds = listOf(0, 90, 1080, 2270),
            children = listOf(searchFrame.copy(path = listOf(0, 0))),
        )
        val host =
            ScriptHost(
                scriptExecutionId = "script-1",
                phoneObserver =
                FakePhoneObserver(
                    listOf(
                        snapshot(elements = listOf(broadWrapper)),
                        snapshot(
                            focusedElementId = "search-input",
                            elements =
                            listOf(
                                element(
                                    id = "search-input",
                                    ref = "e1",
                                    label = "Search",
                                    role = "input",
                                    editable = true,
                                    focused = true,
                                    text = "apple",
                                ),
                            ),
                        ),
                    ),
                ),
                phoneActuator = actuator,
                sessionCoordinator = testSessionCoordinator(InMemorySessionLogStore()),
                logStore = InMemorySessionLogStore(),
            )

        val result = host.locatorFill(
            """{"kind":"label","pattern":"Search for atta, dal, coke and more","flags":"i"}""",
            "apple",
            """{"timeoutMs":1000}""",
        )

        assertTrue(result.message, result.ok)
        assertEquals("search-frame", actuator.lastTapped?.elementId)
        assertEquals("search-input" to "apple", actuator.lastTyped?.let { it.first.elementId to it.second })
    }

    @Test
    fun `locatorFill tries focused typing for search surfaces without exposed editable node`() = runTest {
        val actuator = FakePhoneActuator(
            tapResult = ActionResult.Success("Tapped search."),
            typeResult = ActionResult.Success("Typed focused."),
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
                                element(id = "search-wrapper", ref = "e0", label = "Search", role = "control"),
                            ),
                        ),
                        snapshot(
                            elements =
                            listOf(
                                element(id = "search-wrapper", ref = "e0", label = "Search", role = "control"),
                            ),
                        ),
                        snapshot(
                            elements =
                            listOf(
                                element(id = "result", ref = "e1", label = "apple", text = "apple", role = "control"),
                            ),
                        ),
                    ),
                ),
                phoneActuator = actuator,
                sessionCoordinator = testSessionCoordinator(InMemorySessionLogStore()),
                logStore = InMemorySessionLogStore(),
            )

        val result = host.locatorFill("""{"kind":"label","text":"Search"}""", "apple", """{"timeoutMs":1000}""")

        assertTrue(result.message, result.ok)
        assertEquals("search-wrapper", actuator.lastTapped?.elementId)
        assertEquals("apple", actuator.lastTypedFocused)
        val data = result.data!!.jsonObject
        assertEquals("activate_then_type_focused", data["method"]!!.jsonPrimitive.content)
        assertTrue(data["traceTags"]!!.jsonArray.any { it.jsonPrimitive.content == "focused_input_fallback" })
    }

    @Test
    fun `locatorPress falls back to focused editable when original locator is stale`() = runTest {
        val actuator = FakePhoneActuator(pressEnterResult = ActionResult.Success("Pressed Enter."))
        val host =
            ScriptHost(
                scriptExecutionId = "script-1",
                phoneObserver =
                FakePhoneObserver(
                    listOf(
                        snapshot(
                            focusedElementId = "search-input",
                            elements = listOf(
                                element(
                                    id = "search-input",
                                    ref = "e1",
                                    label = "Search input",
                                    role = "input",
                                    editable = true,
                                    focused = true,
                                    text = "apple",
                                ),
                            ),
                        ),
                    ),
                ),
                phoneActuator = actuator,
                sessionCoordinator = testSessionCoordinator(InMemorySessionLogStore()),
                logStore = InMemorySessionLogStore(),
            )

        val result = host.locatorPress("""{"kind":"testId","testId":"stale-search"}""", "Enter", """{"timeoutMs":0}""")

        assertTrue(result.ok)
        assertEquals("search-input", actuator.lastPressedEnter?.elementId)
        val data = result.data!!.jsonObject
        assertEquals("search-input", data["focusedEditableFallback"]!!.jsonObject["elementId"]!!.jsonPrimitive.content)
        assertEquals("focused_editable_fallback", data["traceTags"]!!.jsonArray.first().jsonPrimitive.content)
    }

    @Test
    fun `deviceCurrent returns compact foreground window and focus state`() = runTest {
        val host =
            ScriptHost(
                scriptExecutionId = "script-1",
                phoneObserver =
                FakePhoneObserver(
                    listOf(
                        snapshot(
                            packageName = "com.example.app",
                            focusedElementId = "search-input",
                            elements = listOf(
                                element(id = "search-input", label = "Search", editable = true, focused = true),
                            ),
                            windows = listOf(
                                ScreenWindow(
                                    packageName = "com.example.app",
                                    className = "Main",
                                    type = "APPLICATION",
                                    layer = 0,
                                    active = true,
                                    focused = true,
                                    bounds = listOf(0, 0, 100, 100),
                                    visibleText = listOf("Search"),
                                    actionableElementCount = 1,
                                    selected = true,
                                ),
                                ScreenWindow(
                                    packageName = "com.android.systemui",
                                    className = "InputMethod",
                                    type = "INPUT_METHOD",
                                    layer = 1,
                                    active = false,
                                    focused = false,
                                    bounds = listOf(0, 80, 100, 100),
                                    visibleText = emptyList(),
                                    actionableElementCount = 0,
                                ),
                            ),
                        ),
                    ),
                ),
                phoneActuator = FakePhoneActuator(),
                sessionCoordinator = testSessionCoordinator(InMemorySessionLogStore()),
                logStore = InMemorySessionLogStore(),
            )

        val result = host.deviceCurrent()

        assertTrue(result.ok)
        val data = result.data!!.jsonObject
        assertEquals("com.example.app", data["foregroundPackage"]!!.jsonPrimitive.content)
        assertTrue(data["systemUiPresent"]!!.jsonPrimitive.content.toBoolean())
        assertTrue(data["keyboardOrInputWindowPresent"]!!.jsonPrimitive.content.toBoolean())
        assertEquals("search-input", data["focusedElement"]!!.jsonObject["elementId"]!!.jsonPrimitive.content)
    }

    @Test
    fun `locatorAssert retries visible assertion`() = runTest {
        var currentTime = Instant.parse("2026-04-16T00:00:00Z")
        val host =
            ScriptHost(
                scriptExecutionId = "script-1",
                phoneObserver =
                FakePhoneObserver(
                    listOf(
                        snapshot(elements = listOf(element(id = "loading", ref = "e0", label = "Loading"))),
                        snapshot(elements = listOf(element(id = "done", ref = "e1", label = "Done", text = "Done"))),
                    ),
                ),
                phoneActuator = FakePhoneActuator(),
                sessionCoordinator = testSessionCoordinator(InMemorySessionLogStore()),
                logStore = InMemorySessionLogStore(),
                now = { currentTime },
                sleeper = { delayMs -> currentTime = currentTime.plusMillis(delayMs) },
            )

        val result = host.locatorAssert("""{"kind":"text","text":"Done"}""", """{"matcher":"toBeVisible","timeoutMs":600}""")

        assertTrue(result.ok)
    }

    @Test
    fun `locatorAssert preserves regex flags for text assertions`() = runTest {
        var currentTime = Instant.parse("2026-04-16T00:00:00Z")
        val host =
            ScriptHost(
                scriptExecutionId = "script-1",
                phoneObserver =
                FakePhoneObserver(
                    listOf(
                        snapshot(elements = listOf(element(id = "done", ref = "e1", label = "done", text = "done"))),
                    ),
                ),
                phoneActuator = FakePhoneActuator(),
                sessionCoordinator = testSessionCoordinator(InMemorySessionLogStore()),
                logStore = InMemorySessionLogStore(),
                now = { currentTime },
                sleeper = { delayMs -> currentTime = currentTime.plusMillis(delayMs) },
            )

        val caseSensitive =
            host.locatorAssert(
                """{"kind":"text","text":"done"}""",
                """{"matcher":"toHaveText","expectedPattern":"^Done$","expectedFlags":"","timeoutMs":0}""",
            )
        val caseInsensitive =
            host.locatorAssert(
                """{"kind":"text","text":"done"}""",
                """{"matcher":"toHaveText","expectedPattern":"^Done$","expectedFlags":"i","timeoutMs":0}""",
            )

        assertFalse(caseSensitive.ok)
        assertEquals("timeout", caseSensitive.errorCode)
        assertTrue(caseInsensitive.ok)
    }

    @Test
    fun `locatorAssert rejects malformed assertion payloads before polling`() = runTest {
        val logStore = InMemorySessionLogStore()
        val host =
            ScriptHost(
                scriptExecutionId = "script-1",
                phoneObserver =
                FakePhoneObserver(
                    listOf(
                        snapshot(elements = listOf(element(id = "done", ref = "e1", label = "Done", text = "Done"))),
                    ),
                ),
                phoneActuator = FakePhoneActuator(),
                sessionCoordinator = testSessionCoordinator(logStore),
                logStore = logStore,
            )

        val result = host.locatorAssert("""{"kind":"text","text":"Done"}""", """{"matcher":"toHaveText","timeoutMs":5000}""")

        assertFalse(result.ok)
        assertEquals("invalid_assertion", result.errorCode)
        assertEquals(0, logStore.recentScreenStates().size)
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
        resourceId: String? = null,
        tapFallbackEligible: Boolean = false,
        clickabilityReason: String = "",
        children: List<ScreenNode> = emptyList(),
        visibleToUser: Boolean = true,
        path: List<Int> = emptyList(),
    ): ScreenNode = ScreenNode(
        path = path,
        ref = ref,
        elementId = id,
        role = role,
        label = label,
        text = text,
        resourceId = resourceId,
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
    private val pressEnterResult: ActionResult = ActionResult.Success("Pressed Enter."),
) : PhoneActuator {
    var lastTapped: ElementRef? = null
    var lastTappedPoint: Pair<Int, Int>? = null
    var lastTyped: Pair<ElementRef, String>? = null
    var lastTypedFocused: String? = null
    var lastScrolled: Pair<ElementRef, ScrollDirection>? = null
    var lastPressedEnter: ElementRef? = null

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

    override suspend fun pressEnter(target: ElementRef): ActionResult {
        lastPressedEnter = target
        return pressEnterResult
    }
}

private class FakeInstalledAppRegistry(private val apps: List<InstalledAppPayload> = emptyList()) : InstalledAppRegistry {
    var launchedPackage: String? = null

    override fun listLaunchableApps(): List<InstalledAppPayload> = apps

    override fun launchPackage(packageName: String): HostCallOutcome {
        launchedPackage = packageName
        return HostCallOutcome(ok = true, message = "Launched package '$packageName'.")
    }
}
