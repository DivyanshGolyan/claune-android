package com.divyanshgolyan.claune.android.accessibility

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.divyanshgolyan.claune.android.BuildConfig
import com.divyanshgolyan.claune.android.runtime.ActionResult
import com.divyanshgolyan.claune.android.runtime.ElementRef
import com.divyanshgolyan.claune.android.runtime.PhoneActuator
import com.divyanshgolyan.claune.android.runtime.PhoneObserver
import com.divyanshgolyan.claune.android.runtime.ScrollDirection
import com.divyanshgolyan.claune.android.runtime.SessionCoordinator
import com.divyanshgolyan.claune.android.runtime.UiElement
import com.divyanshgolyan.claune.android.runtime.UiSnapshot
import java.time.Instant
import java.util.Locale

class AccessibilityBridge(private val sessionCoordinator: SessionCoordinator) :
    PhoneObserver,
    PhoneActuator {
    @Volatile
    private var service: ClauneAccessibilityService? = null

    fun attach(service: ClauneAccessibilityService) {
        this.service = service
    }

    fun detach() {
        service = null
    }

    fun isConnected(): Boolean = service != null

    fun refreshConnectionState() {
        sessionCoordinator.setAccessibilityConnected(isConnected())
    }

    fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val packageName = event?.packageName?.toString()
        if (!packageName.isNullOrBlank()) {
            sessionCoordinator.setLastKnownApp(packageName)
        }
    }

    override suspend fun captureSnapshot(): UiSnapshot {
        val activeService = service
        val root = currentRoot(activeService)
        if (activeService == null || root == null) {
            return UiSnapshot(
                snapshotId = "snapshot-${System.currentTimeMillis()}",
                capturedAt = Instant.now(),
                foregroundPackage = "unavailable",
                visibleText = emptyList(),
                actionableElements = emptyList(),
                focusedElementId = null,
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

    private fun flattenNode(
        node: AccessibilityNodeInfo,
        packageName: String,
        elements: MutableList<UiElement>,
        visibleText: MutableSet<String>,
        path: List<Int>,
    ) {
        val ownText = node.text?.toString()?.trim().orEmpty().ifBlank { null }
        val ownContentDescription = node.contentDescription?.toString()?.trim().orEmpty().ifBlank { null }
        val label = deriveElementLabel(node)

        if (!label.isNullOrBlank()) {
            visibleText += label
        }

        val clickable = node.isClickable
        val editable = node.isEditable
        val scrollable = node.isScrollable
        if (clickable || editable || scrollable) {
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
                    editable = editable,
                    focused = node.isFocused,
                    enabled = node.isEnabled,
                    checked = node.isChecked,
                    selected = node.isSelected,
                    scrollable = scrollable,
                    bounds = node.boundsRect(),
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

    private fun currentRoot(activeService: ClauneAccessibilityService?): AccessibilityNodeInfo? {
        if (activeService == null) {
            return null
        }

        val windows = activeService.windows.orEmpty()
        val roots =
            windows.mapNotNull { it.root }
                .distinctBy { root ->
                    listOf(
                        root.packageName?.toString().orEmpty(),
                        root.className?.toString().orEmpty(),
                        root.windowId,
                    ).joinToString("|")
                }

        if (roots.isEmpty()) {
            return activeService.rootInActiveWindow
        }

        val preferExternalWindow =
            sessionCoordinator.uiState.value.foregroundServiceRunning &&
                !sessionCoordinator.uiState.value.appInForeground

        if (preferExternalWindow) {
            roots.firstOrNull { root ->
                root.packageName?.toString()?.isNotBlank() == true &&
                    root.packageName?.toString() != BuildConfig.APPLICATION_ID
            }?.let { return it }
        }

        return activeService.rootInActiveWindow
            ?: roots.firstOrNull()
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

    private fun collectVisibleText(node: AccessibilityNodeInfo, collector: MutableSet<String>, limit: Int) {
        if (collector.size >= limit) {
            return
        }

        val ownValues =
            listOf(node.text, node.contentDescription)
                .mapNotNull { it?.toString()?.trim()?.takeIf(String::isNotBlank) }

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

    private fun inferRole(node: AccessibilityNodeInfo): String = when {
        node.isEditable -> "input"
        node.className?.contains("Button") == true -> "button"
        node.className?.contains("Switch") == true -> "switch"
        node.isScrollable -> "list"
        else -> "control"
    }

    private companion object {
        private const val DESCENDANT_TEXT_LIMIT = 4
    }
}

private fun AccessibilityNodeInfo.boundsRect(): List<Int> {
    val rect = Rect()
    getBoundsInScreen(rect)
    return listOf(rect.left, rect.top, rect.right, rect.bottom)
}

private const val MAX_PARENT_CLICK_SEARCH_DEPTH = 5
