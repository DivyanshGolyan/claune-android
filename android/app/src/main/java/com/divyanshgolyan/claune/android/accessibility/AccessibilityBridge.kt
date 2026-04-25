package com.divyanshgolyan.claune.android.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.divyanshgolyan.claune.android.BuildConfig
import com.divyanshgolyan.claune.android.runtime.ActionResult
import com.divyanshgolyan.claune.android.runtime.ElementRef
import com.divyanshgolyan.claune.android.runtime.PhoneActuator
import com.divyanshgolyan.claune.android.runtime.PhoneObserver
import com.divyanshgolyan.claune.android.runtime.ScrollDirection
import com.divyanshgolyan.claune.android.runtime.SessionCoordinator
import com.divyanshgolyan.claune.android.runtime.UiElement
import com.divyanshgolyan.claune.android.runtime.UiSnapshot
import com.divyanshgolyan.claune.android.runtime.WindowCandidate
import java.time.Instant
import java.util.Locale
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.Serializable

class AccessibilityBridge(private val context: Context, private val sessionCoordinator: SessionCoordinator) :
    PhoneObserver,
    PhoneActuator {
    @Volatile
    private var service: ClauneAccessibilityService? = null

    fun attach(service: ClauneAccessibilityService) {
        this.service = service
    }

    fun detach(service: ClauneAccessibilityService) {
        if (this.service === service) {
            this.service = null
        }
    }

    fun isConnected(): Boolean = service != null

    fun refreshConnectionState() {
        sessionCoordinator.setAccessibilityConnected(isConnected() || isServiceEnabledInSettings())
    }

    fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val packageName = event?.packageName?.toString()
        refreshConnectionState()
        val shouldTrack = !packageName.isNullOrBlank() && shouldTrackForegroundPackage(packageName)
        if (shouldTrack) {
            sessionCoordinator.setLastKnownApp(packageName)
        }
    }

    private fun shouldTrackForegroundPackage(packageName: String): Boolean = packageName != context.packageName &&
        packageName != SYSTEM_UI_PACKAGE &&
        KEYBOARD_PACKAGE_PREFIXES.none(packageName::startsWith)

    override suspend fun captureSnapshot(): UiSnapshot {
        val activeService = service
        val rootSelection = selectRoot(activeService)
        val root = rootSelection?.root
        if (activeService == null || root == null) {
            return UiSnapshot(
                snapshotId = "snapshot-${System.currentTimeMillis()}",
                capturedAt = Instant.now(),
                foregroundPackage = "unavailable",
                visibleText = emptyList(),
                actionableElements = emptyList(),
                focusedElementId = null,
                windowCandidates = rootSelection?.windowCandidates.orEmpty(),
                selectedWindowReason = rootSelection?.reason,
            )
        }

        val packageName =
            root.packageName
                ?.toString()
                .orEmpty()
                .ifBlank { "unknown" }
        val elements = mutableListOf<UiElement>()
        val visibleText = linkedSetOf<String>()
        flattenNode(
            node = root,
            packageName = packageName,
            elements = elements,
            visibleText = visibleText,
            path = emptyList(),
        )

        return UiSnapshot(
            snapshotId = "snapshot-${System.currentTimeMillis()}",
            capturedAt = Instant.now(),
            foregroundPackage = packageName,
            visibleText = visibleText.toList(),
            actionableElements = elements.take(40),
            focusedElementId = elements.firstOrNull { it.focused }?.id,
            windowCandidates = rootSelection.windowCandidates,
            selectedWindowReason = rootSelection.reason,
        )
    }

    override suspend fun tap(target: ElementRef): ActionResult {
        val node = findNodeByElementId(target.elementId)
            ?: return ActionResult.Blocked("Could not find element '${target.elementId}' in the latest active window.")

        if (node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            return ActionResult.Success("Tapped '${target.elementId}'.")
        }

        var parent = node.parent
        var depth = 0
        while (parent != null && depth < MAX_PARENT_CLICK_SEARCH_DEPTH) {
            if (parent.isClickable && parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                return ActionResult.Success("Tapped clickable parent for '${target.elementId}'.")
            }
            parent = parent.parent
            depth += 1
        }

        findClickableDescendant(node)?.let { descendant ->
            if (descendant.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                return ActionResult.Success("Tapped clickable descendant for '${target.elementId}'.")
            }
        }

        val bounds = node.visibleSnapshotBounds()
        if (bounds != null && dispatchTapGesture(boundsListCenterX(bounds), boundsListCenterY(bounds))) {
            return ActionResult.Success("Tapped gesture fallback for '${target.elementId}'.")
        }

        return ActionResult.Blocked("Element '${target.elementId}' is visible but did not accept a click action.")
    }

    override suspend fun type(target: ElementRef, text: String): ActionResult {
        val node = findNodeByElementId(target.elementId)
            ?: return ActionResult.Blocked("Could not find element '${target.elementId}' in the latest active window.")

        if (!node.isEditable) {
            return ActionResult.Blocked("Element '${target.elementId}' is not editable.")
        }

        val arguments =
            Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            }
        return if (node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)) {
            ActionResult.Success("Typed into '${target.elementId}'.")
        } else {
            ActionResult.Blocked("System rejected text input for '${target.elementId}'.")
        }
    }

    override suspend fun scroll(target: ElementRef, direction: ScrollDirection): ActionResult {
        val node = findNodeByElementId(target.elementId)
            ?: return ActionResult.Blocked("Could not find element '${target.elementId}' in the latest active window.")

        val action =
            when (direction) {
                ScrollDirection.Up,
                ScrollDirection.Left,
                ->
                    AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD

                ScrollDirection.Down,
                ScrollDirection.Right,
                ->
                    AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
            }

        if (direction == ScrollDirection.Left || direction == ScrollDirection.Right) {
            return ActionResult.Blocked("Horizontal scrolling is not supported in this prototype yet.")
        }

        return if (node.performAction(action)) {
            ActionResult.Success("Scrolled '${target.elementId}' ${direction.name.lowercase(Locale.US)}.")
        } else {
            ActionResult.Blocked("Element '${target.elementId}' did not accept a scroll action.")
        }
    }

    override suspend fun pressBack(): ActionResult {
        val didPerform = service?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK) == true
        return if (didPerform) {
            ActionResult.Success("Pressed back.")
        } else {
            ActionResult.Blocked("Accessibility service is not ready for back actions.")
        }
    }

    override suspend fun pressHome(): ActionResult {
        val didPerform = service?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME) == true
        return if (didPerform) {
            ActionResult.Success("Pressed home.")
        } else {
            ActionResult.Blocked("Accessibility service is not ready for home actions.")
        }
    }

    fun captureRawTreeDump(): RawTreeDump? {
        val rootSelection = selectRoot(service) ?: return null
        return RawTreeDump(
            capturedAt = Instant.now().toString(),
            selectedWindowReason = rootSelection.reason,
            foregroundPackage = rootSelection.root.packageName?.toString().orEmpty().ifBlank { "unknown" },
            nodes = dumpNode(rootSelection.root, emptyList()),
        )
    }

    private fun flattenNode(
        node: AccessibilityNodeInfo,
        packageName: String,
        elements: MutableList<UiElement>,
        visibleText: MutableSet<String>,
        path: List<Int>,
    ) {
        val bounds = node.visibleSnapshotBounds()
        val ownText = node.text?.toString()?.trim().orEmpty().ifBlank { null }
        val ownContentDescription = node.contentDescription?.toString()?.trim().orEmpty().ifBlank { null }
        val label = deriveElementLabel(node)

        if (bounds != null && !label.isNullOrBlank()) {
            visibleText += label
        }

        val clickable = node.isClickable
        val focusable = node.isFocusable
        val editable = node.isEditable
        val scrollable = node.isScrollable
        if (bounds != null && shouldExportNode(node, ownText, ownContentDescription, clickable, focusable, editable, scrollable)) {
            val ref = buildElementRef(path)
            elements +=
                UiElement(
                    id = buildElementId(packageName, node, path, label),
                    ref = ref,
                    role = inferRole(node),
                    label = label.orEmpty(),
                    text = ownText,
                    contentDescription = ownContentDescription,
                    resourceId = node.viewIdResourceName,
                    className = node.className?.toString(),
                    clickable = clickable,
                    focusable = focusable,
                    editable = editable,
                    focused = node.isFocused,
                    enabled = node.isEnabled,
                    checked = node.isChecked,
                    selected = node.isSelected,
                    scrollable = scrollable,
                    bounds = bounds,
                )
        }

        for (index in 0 until node.childCount) {
            node.getChild(index)?.let { child ->
                flattenNode(
                    node = child,
                    packageName = packageName,
                    elements = elements,
                    visibleText = visibleText,
                    path = path + index,
                )
            }
        }
    }

    private fun findNodeByElementId(elementId: String): AccessibilityNodeInfo? {
        val root = currentRoot(service) ?: return null
        return findNodeByElementId(root, elementId)
    }

    private fun currentRoot(activeService: ClauneAccessibilityService?): AccessibilityNodeInfo? = selectRoot(activeService)?.root

    private fun selectRoot(activeService: ClauneAccessibilityService?): RootSelection? {
        if (activeService == null) {
            return null
        }

        val candidates =
            activeService.windows.orEmpty()
                .mapNotNull { window -> window.root?.let { root -> RootCandidate.from(window, root) } }
                .distinctBy { candidate ->
                    listOf(
                        candidate.packageName,
                        candidate.className.orEmpty(),
                        candidate.root.windowId,
                    ).joinToString("|")
                }.ifEmpty {
                    activeService.rootInActiveWindow?.let { root ->
                        listOf(RootCandidate.fromActiveRoot(root))
                    }.orEmpty()
                }

        if (candidates.isEmpty()) {
            return null
        }

        val preferExternalWindow =
            sessionCoordinator.uiState.value.foregroundServiceRunning &&
                !sessionCoordinator.uiState.value.appInForeground

        val selected =
            candidates
                .maxWithOrNull(
                    compareBy<RootCandidate> { candidate ->
                        candidate.selectionScore(preferExternalWindow)
                    }.thenBy { candidate -> candidate.layer },
                )
                ?: return null
        val reason =
            when {
                selected.isBareSystemNavigationRoot() ->
                    "Selected fallback System UI navigation root because no better app window was available."
                selected.packageName == BuildConfig.APPLICATION_ID ->
                    "Selected Claune app root because no better external app window was available."
                else ->
                    "Selected ${selected.packageName} over ${candidates.size - 1} other accessibility window(s)."
            }

        return RootSelection(
            root = selected.root,
            reason = reason,
            windowCandidates = candidates.map {
                it.toWindowCandidate(
                    selected = it === selected,
                    reason = if (it ===
                        selected
                    ) {
                        reason
                    } else {
                        null
                    },
                )
            },
        )
    }

    private fun findNodeByElementId(node: AccessibilityNodeInfo, elementId: String): AccessibilityNodeInfo? {
        val packageName = node.packageName?.toString().orEmpty().ifBlank { "unknown" }
        return findNodeByElementId(
            node = node,
            packageName = packageName,
            elementId = elementId,
            path = emptyList(),
        )
    }

    private fun findNodeByElementId(
        node: AccessibilityNodeInfo,
        packageName: String,
        elementId: String,
        path: List<Int>,
    ): AccessibilityNodeInfo? {
        val label = deriveElementLabel(node)
        if (buildElementId(packageName, node, path, label) == elementId) {
            return node
        }

        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            val match = findNodeByElementId(child, packageName, elementId, path + index)
            if (match != null) {
                return match
            }
        }

        return null
    }

    private fun buildElementId(packageName: String, node: AccessibilityNodeInfo, path: List<Int>, label: String?): String {
        val parts =
            listOfNotNull(
                packageName,
                node.className?.toString(),
                node.viewIdResourceName,
                label,
                path.joinToString(separator = "_"),
            )
        return parts
            .joinToString(separator = "|")
            .lowercase(Locale.US)
            .replace(" ", "_")
    }

    private fun buildElementRef(path: List<Int>): String = "e${path.joinToString(separator = "_").ifBlank { "0" }}"

    private fun deriveElementLabel(node: AccessibilityNodeInfo): String? {
        val candidates = linkedSetOf<String>()
        collectVisibleText(node, candidates, limit = DESCENDANT_TEXT_LIMIT)
        return candidates.firstOrNull { it.isNotBlank() }
    }

    private fun shouldExportNode(
        node: AccessibilityNodeInfo,
        ownText: String?,
        ownContentDescription: String?,
        clickable: Boolean,
        focusable: Boolean,
        editable: Boolean,
        scrollable: Boolean,
    ): Boolean {
        if (clickable || focusable || editable || scrollable) {
            return true
        }
        val hasStableIdentity = !node.viewIdResourceName.isNullOrBlank() || !ownContentDescription.isNullOrBlank()
        val hasUsefulClassHint = node.className?.toString()?.contains("EditText", ignoreCase = true) == true
        return node.isImportantForAccessibility &&
            (hasStableIdentity || hasUsefulClassHint) &&
            (node.childCount > 0 || !ownText.isNullOrBlank())
    }

    private fun dumpNode(node: AccessibilityNodeInfo, path: List<Int>): RawNodeDump {
        val children =
            buildList {
                for (index in 0 until node.childCount) {
                    val child = node.getChild(index) ?: continue
                    add(dumpNode(child, path + index))
                }
            }
        return RawNodeDump(
            path = path.joinToString(separator = "_").ifBlank { "root" },
            className = node.className?.toString(),
            packageName = node.packageName?.toString(),
            text = node.text?.toString(),
            contentDescription = node.contentDescription?.toString(),
            resourceId = node.viewIdResourceName,
            visibleToUser = node.isVisibleToUser,
            clickable = node.isClickable,
            focusable = node.isFocusable,
            focused = node.isFocused,
            editable = node.isEditable,
            enabled = node.isEnabled,
            scrollable = node.isScrollable,
            importantForAccessibility = node.isImportantForAccessibility,
            bounds = node.visibleSnapshotBounds() ?: node.boundsRect(),
            childCount = node.childCount,
            children = children,
        )
    }

    private fun collectVisibleText(node: AccessibilityNodeInfo, collector: MutableSet<String>, limit: Int) {
        if (collector.size >= limit) {
            return
        }

        val ownValues =
            if (node.visibleSnapshotBounds() != null) {
                listOf(node.text, node.contentDescription)
                    .mapNotNull { it?.toString()?.trim()?.takeIf(String::isNotBlank) }
            } else {
                emptyList()
            }

        ownValues.forEach { value ->
            if (collector.size < limit) {
                collector += value
            }
        }

        for (index in 0 until node.childCount) {
            if (collector.size >= limit) {
                return
            }
            node.getChild(index)?.let { child ->
                collectVisibleText(child, collector, limit)
            }
        }
    }

    private fun findClickableDescendant(node: AccessibilityNodeInfo, depth: Int = 0): AccessibilityNodeInfo? {
        if (depth >= MAX_DESCENDANT_CLICK_SEARCH_DEPTH) {
            return null
        }
        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            if (child.isClickable) {
                return child
            }
            findClickableDescendant(child, depth + 1)?.let { return it }
        }
        return null
    }

    private suspend fun dispatchTapGesture(centerX: Int, centerY: Int): Boolean {
        val activeService = service ?: return false
        val path = Path().apply { moveTo(centerX.toFloat(), centerY.toFloat()) }
        val gesture =
            GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0L, TAP_GESTURE_DURATION_MS))
                .build()
        return suspendCancellableCoroutine { continuation ->
            val dispatched =
                activeService.dispatchGesture(
                    gesture,
                    object : AccessibilityService.GestureResultCallback() {
                        override fun onCompleted(gestureDescription: GestureDescription?) {
                            if (continuation.isActive) {
                                continuation.resume(true)
                            }
                        }

                        override fun onCancelled(gestureDescription: GestureDescription?) {
                            if (continuation.isActive) {
                                continuation.resume(false)
                            }
                        }
                    },
                    null,
                )
            if (!dispatched && continuation.isActive) {
                continuation.resume(false)
            }
        }
    }

    private fun inferRole(node: AccessibilityNodeInfo): String = when {
        node.isEditable -> "input"
        node.className?.contains("Button") == true -> "button"
        node.className?.contains("Switch") == true -> "switch"
        node.isScrollable -> "list"
        else -> "control"
    }

    private companion object {
        private const val DESCENDANT_TEXT_LIMIT = 4
        private const val WINDOW_TEXT_LIMIT = 8
        private const val MAX_DESCENDANT_CLICK_SEARCH_DEPTH = 6
        private const val TAP_GESTURE_DURATION_MS = 40L
        private const val CLAUNE_ACCESSIBILITY_SERVICE =
            "${BuildConfig.APPLICATION_ID}/com.divyanshgolyan.claune.android.accessibility.ClauneAccessibilityService"
    }

    private fun isServiceEnabledInSettings(): Boolean {
        val enabledServices =
            Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ).orEmpty()
        return enabledServices.split(':').any { it.equals(CLAUNE_ACCESSIBILITY_SERVICE, ignoreCase = true) }
    }
}

private fun AccessibilityNodeInfo.boundsRect(): List<Int> {
    val rect = Rect()
    getBoundsInScreen(rect)
    return listOf(rect.left, rect.top, rect.right, rect.bottom)
}

private fun AccessibilityNodeInfo.visibleSnapshotBounds(): List<Int>? {
    if (!isVisibleToUser) {
        return null
    }
    val rect = Rect()
    getBoundsInScreen(rect)
    if (rect.isEmpty || rect.width() <= 0 || rect.height() <= 0) {
        return null
    }
    return listOf(rect.left, rect.top, rect.right, rect.bottom)
}

private fun boundsListCenterX(bounds: List<Int>): Int = (bounds[0] + bounds[2]) / 2

private fun boundsListCenterY(bounds: List<Int>): Int = (bounds[1] + bounds[3]) / 2

private data class RootSelection(val root: AccessibilityNodeInfo, val reason: String, val windowCandidates: List<WindowCandidate>)

@Serializable
data class RawTreeDump(val capturedAt: String, val selectedWindowReason: String, val foregroundPackage: String, val nodes: RawNodeDump)

@Serializable
data class RawNodeDump(
    val path: String,
    val className: String? = null,
    val packageName: String? = null,
    val text: String? = null,
    val contentDescription: String? = null,
    val resourceId: String? = null,
    val visibleToUser: Boolean,
    val clickable: Boolean,
    val focusable: Boolean,
    val focused: Boolean,
    val editable: Boolean,
    val enabled: Boolean,
    val scrollable: Boolean,
    val importantForAccessibility: Boolean,
    val bounds: List<Int>,
    val childCount: Int,
    val children: List<RawNodeDump>,
)

private data class RootCandidate(
    val root: AccessibilityNodeInfo,
    val packageName: String,
    val className: String?,
    val type: Int,
    val typeLabel: String,
    val layer: Int,
    val active: Boolean,
    val focused: Boolean,
    val bounds: List<Int>,
    val visibleText: List<String>,
    val actionableElementCount: Int,
) {
    fun selectionScore(preferExternalWindow: Boolean): Int {
        var score = boundsArea() / 100_000
        score += actionableElementCount.coerceAtMost(40) * 3
        score += visibleText.size.coerceAtMost(20) * 2
        if (active) score += 40
        if (focused) score += 50
        if (type == AccessibilityWindowInfo.TYPE_APPLICATION) score += 500
        if (packageName != SYSTEM_UI_PACKAGE) score += 1_000
        if (preferExternalWindow && packageName != BuildConfig.APPLICATION_ID) score += 250
        if (packageName == BuildConfig.APPLICATION_ID) score -= 8_000
        if (type == AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY) score -= 9_000
        if (isBareSystemNavigationRoot()) score -= 10_000
        return score
    }

    fun isBareSystemNavigationRoot(): Boolean = packageName == SYSTEM_UI_PACKAGE &&
        actionableElementCount <= 3 &&
        visibleText.isNotEmpty() &&
        visibleText.all { it in SYSTEM_NAVIGATION_LABELS }

    fun toWindowCandidate(selected: Boolean, reason: String?): WindowCandidate = WindowCandidate(
        packageName = packageName,
        className = className,
        type = typeLabel,
        layer = layer,
        active = active,
        focused = focused,
        bounds = bounds,
        visibleText = visibleText,
        actionableElementCount = actionableElementCount,
        selected = selected,
        selectionReason = reason,
    )

    private fun boundsArea(): Int {
        if (bounds.size < 4) return 0
        val width = (bounds[2] - bounds[0]).coerceAtLeast(0)
        val height = (bounds[3] - bounds[1]).coerceAtLeast(0)
        return width * height
    }

    companion object {
        fun from(window: AccessibilityWindowInfo, root: AccessibilityNodeInfo): RootCandidate {
            val windowBounds = Rect()
            window.getBoundsInScreen(windowBounds)
            return RootCandidate(
                root = root,
                packageName = root.packageName?.toString().orEmpty().ifBlank { "unknown" },
                className = root.className?.toString(),
                type = window.type,
                typeLabel = window.typeLabel(),
                layer = window.layer,
                active = window.isActive,
                focused = window.isFocused,
                bounds = windowBounds.toBoundsList().ifEmpty { root.boundsRect() },
                visibleText = collectWindowText(root),
                actionableElementCount = countActionableElements(root),
            )
        }

        fun fromActiveRoot(root: AccessibilityNodeInfo): RootCandidate = RootCandidate(
            root = root,
            packageName = root.packageName?.toString().orEmpty().ifBlank { "unknown" },
            className = root.className?.toString(),
            type = -1,
            typeLabel = "ROOT_IN_ACTIVE_WINDOW",
            layer = 0,
            active = true,
            focused = root.isFocused,
            bounds = root.boundsRect(),
            visibleText = collectWindowText(root),
            actionableElementCount = countActionableElements(root),
        )

        private fun collectWindowText(root: AccessibilityNodeInfo): List<String> {
            val values = linkedSetOf<String>()
            collectWindowText(root, values)
            return values.toList()
        }

        private fun collectWindowText(node: AccessibilityNodeInfo, collector: MutableSet<String>) {
            if (collector.size >= WINDOW_TEXT_LIMIT) {
                return
            }

            if (node.visibleSnapshotBounds() != null) {
                listOf(node.text, node.contentDescription)
                    .mapNotNull { it?.toString()?.trim()?.takeIf(String::isNotBlank) }
                    .forEach { value ->
                        if (collector.size < WINDOW_TEXT_LIMIT) {
                            collector += value
                        }
                    }
            }
            for (index in 0 until node.childCount) {
                if (collector.size >= WINDOW_TEXT_LIMIT) {
                    return
                }
                node.getChild(index)?.let { collectWindowText(it, collector) }
            }
        }

        private fun countActionableElements(node: AccessibilityNodeInfo): Int {
            var count =
                if (node.visibleSnapshotBounds() != null && (node.isClickable || node.isEditable || node.isScrollable)) {
                    1
                } else {
                    0
                }
            for (index in 0 until node.childCount) {
                node.getChild(index)?.let { child ->
                    count += countActionableElements(child)
                }
            }
            return count
        }
    }
}

private fun AccessibilityWindowInfo.typeLabel(): String = when (type) {
    AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY -> "ACCESSIBILITY_OVERLAY"
    AccessibilityWindowInfo.TYPE_APPLICATION -> "APPLICATION"
    AccessibilityWindowInfo.TYPE_INPUT_METHOD -> "INPUT_METHOD"
    AccessibilityWindowInfo.TYPE_SPLIT_SCREEN_DIVIDER -> "SPLIT_SCREEN_DIVIDER"
    AccessibilityWindowInfo.TYPE_SYSTEM -> "SYSTEM"
    else -> "UNKNOWN_$type"
}

private fun Rect.toBoundsList(): List<Int> = if (isEmpty()) {
    emptyList()
} else {
    listOf(left, top, right, bottom)
}

private const val MAX_PARENT_CLICK_SEARCH_DEPTH = 5
private const val WINDOW_TEXT_LIMIT = 8
private val KEYBOARD_PACKAGE_PREFIXES =
    listOf(
        "com.android.inputmethod",
        "com.google.android.inputmethod",
        "com.samsung.android.honeyboard",
    )
private const val SYSTEM_UI_PACKAGE = "com.android.systemui"
private val SYSTEM_NAVIGATION_LABELS = setOf("Overview", "Back", "Home")
