package com.divyanshgolyan.claune.android.accessibility

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
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

    override suspend fun tap(target: ElementRef): ActionResult = ActionResult.Blocked("Tap wiring is stubbed until milestone 3.")

    override suspend fun type(target: ElementRef, text: String): ActionResult =
        ActionResult.Blocked("Typing wiring is stubbed until milestone 3.")

    override suspend fun scroll(target: ElementRef, direction: ScrollDirection): ActionResult =
        ActionResult.Blocked("Scroll wiring is stubbed until milestone 3.")

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
