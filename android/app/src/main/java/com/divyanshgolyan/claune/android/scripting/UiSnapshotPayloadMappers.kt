package com.divyanshgolyan.claune.android.scripting

import com.divyanshgolyan.claune.android.runtime.UiElement
import com.divyanshgolyan.claune.android.runtime.UiSnapshot

fun UiSnapshot.toPayload(): UiSnapshotPayload = UiSnapshotPayload(
    snapshotId = snapshotId,
    capturedAt = capturedAt.toString(),
    foregroundPackage = foregroundPackage,
    visibleText = visibleText,
    actionableElements = actionableElements.map { it.toPayload() },
    focusedElementId = focusedElementId,
)

fun UiElement.toPayload(): UiElementPayload = UiElementPayload(
    id = id,
    ref = ref,
    role = role,
    label = label,
    text = text,
    contentDescription = contentDescription,
    resourceId = resourceId,
    className = className,
    clickable = clickable,
    editable = editable,
    focused = focused,
    enabled = enabled,
    checked = checked,
    selected = selected,
    scrollable = scrollable,
    bounds = bounds,
)
