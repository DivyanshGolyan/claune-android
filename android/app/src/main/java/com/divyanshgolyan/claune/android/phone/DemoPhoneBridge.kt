package com.divyanshgolyan.claune.android.phone

import com.divyanshgolyan.claune.android.runtime.ActionResult
import com.divyanshgolyan.claune.android.runtime.ElementRef
import com.divyanshgolyan.claune.android.runtime.PhoneActuator
import com.divyanshgolyan.claune.android.runtime.PhoneObserver
import com.divyanshgolyan.claune.android.runtime.ScrollDirection
import com.divyanshgolyan.claune.android.runtime.UiElement
import com.divyanshgolyan.claune.android.runtime.UiSnapshot
import java.time.Instant
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class DemoPhoneBridge :
    PhoneObserver,
    PhoneActuator {
    private val lock = Mutex()
    private var currentScreen: DemoScreen = DemoScreen.Launcher
    private val backStack = ArrayDeque<DemoScreen>()
    private var searchQuery: String = ""
    private var settingsScrolledToEnd: Boolean = false

    override suspend fun captureSnapshot(): UiSnapshot = lock.withLock {
        buildSnapshot()
    }

    override suspend fun tap(target: ElementRef): ActionResult = lock.withLock {
        when (target.elementId) {
            DemoElementIds.LAUNCHER_SETTINGS -> navigateTo(DemoScreen.SettingsHome, "Opened Settings from the demo launcher.")
            DemoElementIds.SETTINGS_NETWORK -> navigateTo(DemoScreen.WifiPage, "Opened the Network & internet page.")
            DemoElementIds.SETTINGS_APPS -> navigateTo(DemoScreen.AppsPage, "Opened the Apps page.")
            DemoElementIds.SETTINGS_SEARCH -> navigateTo(DemoScreen.SettingsSearch, "Focused the Settings search field.")
            DemoElementIds.WIFI_SAVED_NETWORKS -> navigateTo(DemoScreen.SavedNetworks, "Opened Saved networks.")
            DemoElementIds.APPS_CLAUNE -> navigateTo(DemoScreen.AppInfo, "Opened the Claune Android app info page.")
            DemoElementIds.SEARCH_RESULT_WIFI -> navigateTo(DemoScreen.WifiPage, "Opened Wi-Fi from search results.")
            else -> ActionResult.Blocked("Demo phone could not find element '${target.elementId}'.")
        }
    }

    override suspend fun type(target: ElementRef, text: String): ActionResult = lock.withLock {
        if (currentScreen != DemoScreen.SettingsSearch || target.elementId != DemoElementIds.SEARCH_INPUT) {
            return@withLock ActionResult.Blocked("Element '${target.elementId}' is not editable on the current demo screen.")
        }
        searchQuery = text
        ActionResult.Success("Entered '$text' into the demo search field.")
    }

    override suspend fun scroll(target: ElementRef, direction: ScrollDirection): ActionResult = lock.withLock {
        if (target.elementId != DemoElementIds.SETTINGS_LIST) {
            return@withLock ActionResult.Blocked("Element '${target.elementId}' is not scrollable on the demo phone.")
        }
        if (currentScreen != DemoScreen.SettingsHome) {
            return@withLock ActionResult.Blocked("Settings list scrolling is only available on the Settings home screen.")
        }
        if (direction == ScrollDirection.Left || direction == ScrollDirection.Right) {
            return@withLock ActionResult.Blocked("Horizontal scrolling is not supported on the demo phone.")
        }

        settingsScrolledToEnd =
            when (direction) {
                ScrollDirection.Down -> true
                ScrollDirection.Up -> false
                ScrollDirection.Left,
                ScrollDirection.Right,
                -> settingsScrolledToEnd
            }
        val destination = if (settingsScrolledToEnd) "lower settings rows" else "top of the settings list"
        ActionResult.Success("Scrolled to the $destination.")
    }

    override suspend fun pressBack(): ActionResult = lock.withLock {
        val previousScreen = backStack.removeLastOrNull()
            ?: return@withLock ActionResult.Blocked("Demo phone is already at the root screen.")
        currentScreen = previousScreen
        if (currentScreen != DemoScreen.SettingsSearch) {
            searchQuery = ""
        }
        ActionResult.Success("Returned to ${currentScreen.title}.")
    }

    override suspend fun pressHome(): ActionResult = lock.withLock {
        currentScreen = DemoScreen.Launcher
        backStack.clear()
        searchQuery = ""
        settingsScrolledToEnd = false
        ActionResult.Success("Returned to the demo launcher.")
    }

    private fun navigateTo(destination: DemoScreen, message: String): ActionResult {
        backStack += currentScreen
        currentScreen = destination
        if (destination != DemoScreen.SettingsSearch) {
            searchQuery = ""
        }
        return ActionResult.Success(message)
    }

    private fun buildSnapshot(): UiSnapshot = UiSnapshot(
        snapshotId = "demo-snapshot-${System.currentTimeMillis()}",
        capturedAt = Instant.now(),
        foregroundPackage = currentScreen.packageName,
        visibleText = visibleTextForScreen(),
        actionableElements = elementsForScreen(),
        focusedElementId =
        if (currentScreen == DemoScreen.SettingsSearch) {
            DemoElementIds.SEARCH_INPUT
        } else {
            null
        },
    )

    private fun visibleTextForScreen(): List<String> = when (currentScreen) {
        DemoScreen.Launcher ->
            listOf("Demo launcher", "Settings", "Messages", "Camera")

        DemoScreen.SettingsHome -> {
            val base = mutableListOf("Settings", "Network & internet", "Apps", "Search settings")
            if (settingsScrolledToEnd) {
                base += listOf("Battery", "System")
            } else {
                base += listOf("Display", "Sound")
            }
            base
        }

        DemoScreen.WifiPage ->
            listOf("Wi-Fi", "Use Wi-Fi", "ClauneNet", "Saved networks")

        DemoScreen.SavedNetworks ->
            listOf("Saved networks", "ClauneNet", "Office Wi-Fi")

        DemoScreen.AppsPage ->
            listOf("Apps", "Claune Android", "Chrome", "Files")

        DemoScreen.AppInfo ->
            listOf("Claune Android", "Notifications", "Permissions", "Storage & cache")

        DemoScreen.SettingsSearch ->
            buildList {
                add("Search settings")
                add(searchQuery.ifBlank { "Type to search" })
                if (searchQuery.contains("wi", ignoreCase = true)) {
                    add("Wi-Fi")
                }
            }
    }

    private fun elementsForScreen(): List<UiElement> = when (currentScreen) {
        DemoScreen.Launcher ->
            listOf(
                control(DemoElementIds.LAUNCHER_SETTINGS, "button", "Settings"),
                control("demo|launcher|messages", "button", "Messages"),
                control("demo|launcher|camera", "button", "Camera"),
            )

        DemoScreen.SettingsHome ->
            buildList {
                add(control(DemoElementIds.SETTINGS_SEARCH, "input", "Search settings", editable = true))
                add(control(DemoElementIds.SETTINGS_NETWORK, "control", "Network & internet"))
                add(control(DemoElementIds.SETTINGS_APPS, "control", "Apps"))
                add(control(DemoElementIds.SETTINGS_LIST, "list", "Settings list"))
                if (settingsScrolledToEnd) {
                    add(control("demo|settings|battery", "control", "Battery"))
                    add(control("demo|settings|system", "control", "System"))
                } else {
                    add(control("demo|settings|display", "control", "Display"))
                    add(control("demo|settings|sound", "control", "Sound"))
                }
            }

        DemoScreen.WifiPage ->
            listOf(
                control("demo|wifi|toggle", "switch", "Use Wi-Fi"),
                control("demo|wifi|claunenet", "control", "ClauneNet"),
                control(DemoElementIds.WIFI_SAVED_NETWORKS, "control", "Saved networks"),
            )

        DemoScreen.SavedNetworks ->
            listOf(
                control("demo|saved_networks|claunenet", "control", "ClauneNet"),
                control("demo|saved_networks|office_wifi", "control", "Office Wi-Fi"),
            )

        DemoScreen.AppsPage ->
            listOf(
                control(DemoElementIds.APPS_CLAUNE, "control", "Claune Android"),
                control("demo|apps|chrome", "control", "Chrome"),
                control("demo|apps|files", "control", "Files"),
            )

        DemoScreen.AppInfo ->
            listOf(
                control("demo|app_info|permissions", "control", "Permissions"),
                control("demo|app_info|notifications", "control", "Notifications"),
            )

        DemoScreen.SettingsSearch ->
            buildList {
                add(
                    control(
                        DemoElementIds.SEARCH_INPUT,
                        "input",
                        if (searchQuery.isBlank()) "Search settings" else searchQuery,
                        editable = true,
                        focused = true,
                    ),
                )
                if (searchQuery.contains("wi", ignoreCase = true)) {
                    add(control(DemoElementIds.SEARCH_RESULT_WIFI, "control", "Wi-Fi"))
                }
            }
    }

    private fun control(
        id: String,
        role: String,
        label: String,
        clickable: Boolean = true,
        editable: Boolean = false,
        focused: Boolean = false,
        bounds: List<Int> = listOf(0, 0, 100, 48),
    ): UiElement = UiElement(
        id = id,
        role = role,
        label = label,
        clickable = clickable,
        editable = editable,
        focused = focused,
        bounds = bounds,
    )
}

private enum class DemoScreen(val title: String, val packageName: String) {
    Launcher(title = "Demo launcher", packageName = "com.demo.launcher"),
    SettingsHome(title = "Settings", packageName = "com.android.settings"),
    WifiPage(title = "Wi-Fi", packageName = "com.android.settings"),
    SavedNetworks(title = "Saved networks", packageName = "com.android.settings"),
    AppsPage(title = "Apps", packageName = "com.android.settings"),
    AppInfo(title = "Claune Android app info", packageName = "com.android.settings"),
    SettingsSearch(title = "Search settings", packageName = "com.android.settings"),
}

private object DemoElementIds {
    const val LAUNCHER_SETTINGS = "demo|launcher|settings"
    const val SETTINGS_SEARCH = "demo|settings|search"
    const val SEARCH_INPUT = "demo|settings|search_input"
    const val SEARCH_RESULT_WIFI = "demo|settings_search|wifi"
    const val SETTINGS_NETWORK = "demo|settings|network_internet"
    const val SETTINGS_APPS = "demo|settings|apps"
    const val SETTINGS_LIST = "demo|settings|list"
    const val WIFI_SAVED_NETWORKS = "demo|wifi|saved_networks"
    const val APPS_CLAUNE = "demo|apps|claune_android"
}
