package com.divyanshgolyan.claune.android.accessibility

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
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

    fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val packageName = event?.packageName?.toString()
        if (!packageName.isNullOrBlank()) {
            sessionCoordinator.setLastKnownApp(packageName)
        }
    }

    override suspend fun captureSnapshot(): UiSnapshot {
        val activeService = service
        val root = activeService?.rootInActiveWindow
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
        flattenNode(root, packageName, elements, visibleText)

        return UiSnapshot(
            snapshotId = "snapshot-${System.currentTimeMillis()}",
            capturedAt = Instant.now(),
            foregroundPackage = packageName,
            visibleText = visibleText.toList(),
            actionableElements = elements.take(24),
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
    ) {
        val label =
            listOf(node.text, node.contentDescription)
                .mapNotNull { it?.toString()?.trim() }
                .firstOrNull { it.isNotEmpty() }

        if (!label.isNullOrBlank()) {
            visibleText += label
        }

        val clickable = node.isClickable
        val editable = node.isEditable
        if (clickable || editable) {
            elements +=
                UiElement(
                    id = buildElementId(packageName, node, label),
                    role = inferRole(node),
                    label = label.orEmpty(),
                    clickable = clickable,
                    editable = editable,
                    focused = node.isFocused,
                    bounds = node.boundsRect(),
                )
        }

        for (index in 0 until node.childCount) {
            node.getChild(index)?.let { child ->
                flattenNode(child, packageName, elements, visibleText)
            }
        }
    }

    private fun findNodeByElementId(elementId: String): AccessibilityNodeInfo? {
        val root = service?.rootInActiveWindow ?: return null
        return findNodeByElementId(root, elementId)
    }

    private fun findNodeByElementId(node: AccessibilityNodeInfo, elementId: String): AccessibilityNodeInfo? {
        val label =
            listOf(node.text, node.contentDescription)
                .mapNotNull { it?.toString()?.trim() }
                .firstOrNull { it.isNotEmpty() }
        val packageName =
            node.packageName
                ?.toString()
                .orEmpty()
                .ifBlank { "unknown" }
        if (buildElementId(packageName, node, label) == elementId) {
            return node
        }

        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            val match = findNodeByElementId(child, elementId)
            if (match != null) {
                return match
            }
        }

        return null
    }

    private fun buildElementId(packageName: String, node: AccessibilityNodeInfo, label: String?): String {
        val parts =
            listOfNotNull(
                packageName,
                node.className?.toString(),
                node.viewIdResourceName,
                label,
            )
        return parts
            .joinToString(separator = "|")
            .lowercase(Locale.US)
            .replace(" ", "_")
    }

    private fun inferRole(node: AccessibilityNodeInfo): String = when {
        node.isEditable -> "input"
        node.className?.contains("Button") == true -> "button"
        node.className?.contains("Switch") == true -> "switch"
        else -> "control"
    }
}

private fun AccessibilityNodeInfo.boundsRect(): List<Int> {
    val rect = Rect()
    getBoundsInScreen(rect)
    return listOf(rect.left, rect.top, rect.right, rect.bottom)
}

private const val MAX_PARENT_CLICK_SEARCH_DEPTH = 5
