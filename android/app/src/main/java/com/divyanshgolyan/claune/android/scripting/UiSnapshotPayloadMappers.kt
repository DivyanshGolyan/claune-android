package com.divyanshgolyan.claune.android.scripting

import com.divyanshgolyan.claune.android.runtime.UiElement
import com.divyanshgolyan.claune.android.runtime.UiSnapshot
import com.divyanshgolyan.claune.android.runtime.WindowCandidate

fun UiSnapshot.toPayload(): UiSnapshotPayload = UiSnapshotPayload(
    snapshotId = snapshotId,
    capturedAt = capturedAt.toString(),
    foregroundPackage = foregroundPackage,
    visibleText = visibleText,
    actionableElements = actionableElements.map { it.toPayload() },
    focusedElementId = focusedElementId,
    windowCandidates = windowCandidates.map { it.toPayload() },
    selectedWindowReason = selectedWindowReason,
)

fun WindowCandidate.toPayload(): WindowCandidatePayload = WindowCandidatePayload(
    packageName = packageName,
    className = className,
    type = type,
    layer = layer,
    active = active,
    focused = focused,
    bounds = bounds,
    visibleText = visibleText,
    actionableElementCount = actionableElementCount,
    selected = selected,
    selectionReason = selectionReason,
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
    focusable = focusable,
    editable = editable,
    focused = focused,
    enabled = enabled,
    checked = checked,
    selected = selected,
    scrollable = scrollable,
    bounds = bounds,
)
