package com.divyanshgolyan.claune.android.scripting

import com.divyanshgolyan.claune.android.data.local.SessionLogStore
import com.divyanshgolyan.claune.android.runtime.ActionResult
import com.divyanshgolyan.claune.android.runtime.ElementRef
import com.divyanshgolyan.claune.android.runtime.PhoneActuator
import com.divyanshgolyan.claune.android.runtime.PhoneObserver
import com.divyanshgolyan.claune.android.runtime.ScrollDirection
import com.divyanshgolyan.claune.android.runtime.SessionCoordinator
import com.divyanshgolyan.claune.android.runtime.UiElement
import com.divyanshgolyan.claune.android.runtime.UiSnapshot
import java.time.Instant
import kotlinx.coroutines.delay
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class ScriptHost(
    private val scriptExecutionId: String,
    private val phoneObserver: PhoneObserver,
    private val phoneActuator: PhoneActuator,
    private val sessionCoordinator: SessionCoordinator,
    private val logStore: SessionLogStore,
    private val now: () -> Instant = { Instant.now() },
    private val sleeper: suspend (Long) -> Unit = { delay(it) },
) {
    private val sessionId: String? get() = sessionCoordinator.uiState.value.sessionId
    private val recordedCalls = mutableListOf<HostCallRecord>()

    fun hostCalls(): List<HostCallRecord> = recordedCalls.toList()

    suspend fun observePhone(): UiSnapshotPayload {
        val snapshot = phoneObserver.captureSnapshot()
        recordSnapshot(snapshot)
        return snapshot.toPayload()
    }

    suspend fun tapElement(elementId: String): HostCallOutcome = recordCall(
        name = "tapElement",
        arguments = buildJsonObject { put("elementId", elementId) },
    ) {
        phoneActuator.tap(ElementRef(elementId)).toOutcome()
    }

    suspend fun tapRef(ref: String): HostCallOutcome = recordCall(
        name = "tapRef",
        arguments = buildJsonObject { put("ref", ref) },
    ) {
        val snapshot = phoneObserver.captureSnapshot()
        recordSnapshot(snapshot)
        val element = snapshot.actionableElements.firstOrNull { it.ref == ref }
            ?: return@recordCall HostCallOutcome(
                ok = false,
                message = "Ref '$ref' was not found in the current snapshot.",
            )
        phoneActuator.tap(ElementRef(element.id)).toOutcome(
            data =
            buildJsonObject {
                put("matchedRef", element.ref)
                put("matchedElementId", element.id)
                put("matchedLabel", element.label)
            },
        )
    }

    suspend fun tapSelector(selectorJson: String): HostCallOutcome = recordCall(
        name = "tapSelector",
        arguments = buildJsonObject { put("selector", selectorJson) },
    ) {
        val selector = decodeSelector(selectorJson)
            ?: return@recordCall HostCallOutcome(
                ok = false,
                message = "Invalid selector JSON.",
            )
        val snapshot = phoneObserver.captureSnapshot()
        recordSnapshot(snapshot)
        val element = selectElement(snapshot, selector)
            ?: return@recordCall selectorFailure(selector, snapshot)
        phoneActuator.tap(ElementRef(element.id)).toOutcome(
            data =
            buildJsonObject {
                put("matchedRef", element.ref)
                put("matchedElementId", element.id)
                put("matchedLabel", element.label)
            },
        )
    }

    suspend fun typeIntoElement(elementId: String, text: String): HostCallOutcome = recordCall(
        name = "typeIntoElement",
        arguments =
        buildJsonObject {
            put("elementId", elementId)
            put("text", text)
        },
    ) {
        phoneActuator.type(ElementRef(elementId), text).toOutcome()
    }

    suspend fun typeIntoSelector(selectorJson: String, text: String): HostCallOutcome = recordCall(
        name = "typeIntoSelector",
        arguments =
        buildJsonObject {
            put("selector", selectorJson)
            put("text", text)
        },
    ) {
        val selector = decodeSelector(selectorJson)
            ?: return@recordCall HostCallOutcome(
                ok = false,
                message = "Invalid selector JSON.",
            )
        val snapshot = phoneObserver.captureSnapshot()
        recordSnapshot(snapshot)
        val element = selectElement(snapshot, selector)
            ?: return@recordCall selectorFailure(selector, snapshot)
        phoneActuator.type(ElementRef(element.id), text).toOutcome(
            data =
            buildJsonObject {
                put("matchedRef", element.ref)
                put("matchedElementId", element.id)
                put("matchedLabel", element.label)
            },
        )
    }

    suspend fun typeIntoFocused(text: String): HostCallOutcome = recordCall(
        name = "typeIntoFocused",
        arguments =
        buildJsonObject {
            put("text", text)
        },
    ) {
        val snapshot = phoneObserver.captureSnapshot()
        recordSnapshot(snapshot)
        val element = snapshot.findFocusedEditableElement()
            ?: return@recordCall HostCallOutcome(
                ok = false,
                message = "No focused editable element was found on the current screen.",
            )
        phoneActuator.type(ElementRef(element.id), text).toOutcome(
            data =
            buildJsonObject {
                put("matchedRef", element.ref)
                put("matchedElementId", element.id)
                put("matchedLabel", element.label)
            },
        )
    }

    suspend fun scrollContainer(elementId: String, direction: String): HostCallOutcome = recordCall(
        name = "scrollContainer",
        arguments =
        buildJsonObject {
            put("elementId", elementId)
            put("direction", direction)
        },
    ) {
        val parsedDirection = direction.toScrollDirection()
            ?: return@recordCall HostCallOutcome(
                ok = false,
                message = "Unsupported scroll direction '$direction'.",
            )
        phoneActuator.scroll(ElementRef(elementId), parsedDirection).toOutcome()
    }

    suspend fun pressBack(): HostCallOutcome = recordCall(
        name = "pressBack",
        arguments = buildJsonObject {},
    ) {
        phoneActuator.pressBack().toOutcome()
    }

    suspend fun pressHome(): HostCallOutcome = recordCall(
        name = "pressHome",
        arguments = buildJsonObject {},
    ) {
        phoneActuator.pressHome().toOutcome()
    }

    suspend fun waitForState(type: String, value: String, timeoutMs: Long): HostCallOutcome = recordCall(
        name = "waitForState",
        arguments =
        buildJsonObject {
            put("type", type)
            put("value", value)
            put("timeoutMs", timeoutMs)
        },
    ) {
        waitForStateInternal(type, value, timeoutMs)
    }

    suspend fun waitForSelector(selectorJson: String, timeoutMs: Long): HostCallOutcome = recordCall(
        name = "waitForSelector",
        arguments =
        buildJsonObject {
            put("selector", selectorJson)
            put("timeoutMs", timeoutMs)
        },
    ) {
        val selector = decodeSelector(selectorJson)
            ?: return@recordCall HostCallOutcome(
                ok = false,
                message = "Invalid selector JSON.",
            )
        waitForSelectorInternal(selector, timeoutMs)
    }

    private suspend fun waitForStateInternal(type: String, value: String, timeoutMs: Long): HostCallOutcome {
        val deadline = now().plusMillis(timeoutMs.coerceAtLeast(0L))
        while (true) {
            val snapshot = phoneObserver.captureSnapshot()
            recordSnapshot(snapshot)

            val matched =
                when (type) {
                    "package" -> snapshot.foregroundPackage == value
                    "element" -> snapshot.actionableElements.any { it.id == value }
                    "text" -> snapshot.visibleText.any { it.contains(value, ignoreCase = true) }
                    else -> {
                        return HostCallOutcome(
                            ok = false,
                            message = "Unsupported waitForState type '$type'.",
                        )
                    }
                }

            if (matched) {
                return HostCallOutcome(
                    ok = true,
                    message = "Matched $type condition for '$value'.",
                )
            }

            if (now().isAfter(deadline)) {
                return HostCallOutcome(
                    ok = false,
                    message = "Timed out waiting for $type '$value' after ${timeoutMs.coerceAtLeast(0L)}ms.",
                )
            }

            sleeper(POLL_INTERVAL_MS)
        }
    }

    private suspend fun waitForSelectorInternal(selector: ElementSelectorPayload, timeoutMs: Long): HostCallOutcome {
        val deadline = now().plusMillis(timeoutMs.coerceAtLeast(0L))
        while (true) {
            val snapshot = phoneObserver.captureSnapshot()
            recordSnapshot(snapshot)
            val matches = snapshot.actionableElements.filter { it.matches(selector) }

            if (matches.size == 1 || (selector.first && matches.isNotEmpty())) {
                val matched = matches.first()
                return HostCallOutcome(
                    ok = true,
                    message = "Matched selector for '${matched.label.ifBlank { matched.id }}'.",
                    data =
                    buildJsonObject {
                        put("matchedRef", matched.ref)
                        put("matchedElementId", matched.id)
                        put("matchedLabel", matched.label)
                    },
                )
            }

            if (matches.size > 1 && !selector.first) {
                return HostCallOutcome(
                    ok = false,
                    message = "Selector matched multiple elements. Refine the selector or set first=true.",
                )
            }

            if (now().isAfter(deadline)) {
                return HostCallOutcome(
                    ok = false,
                    message = "Timed out waiting for selector after ${timeoutMs.coerceAtLeast(0L)}ms.",
                )
            }

            sleeper(POLL_INTERVAL_MS)
        }
    }

    private fun decodeSelector(selectorJson: String): ElementSelectorPayload? = runCatching {
        ScriptJson.codec.decodeFromString(ElementSelectorPayload.serializer(), selectorJson)
    }.getOrNull()?.takeIf { it.hasCriteria() }

    private fun UiSnapshot.findFocusedEditableElement(): UiElement? =
        actionableElements.firstOrNull { it.id == focusedElementId && it.editable }
            ?: actionableElements.firstOrNull { it.focused && it.editable }

    private fun selectElement(snapshot: UiSnapshot, selector: ElementSelectorPayload): UiElement? {
        val matches = snapshot.actionableElements.filter { it.matches(selector) }
        return when {
            matches.isEmpty() -> null
            matches.size == 1 -> matches.first()
            selector.first -> matches.first()
            else -> null
        }
    }

    private fun selectorFailure(selector: ElementSelectorPayload, snapshot: UiSnapshot): HostCallOutcome {
        val matches = snapshot.actionableElements.filter { it.matches(selector) }
        return when {
            matches.isEmpty() ->
                HostCallOutcome(
                    ok = false,
                    message = "Selector did not match any actionable element on the current screen.",
                )

            else ->
                HostCallOutcome(
                    ok = false,
                    message = "Selector matched multiple elements. Refine the selector or set first=true.",
                )
        }
    }

    private suspend fun recordCall(name: String, arguments: JsonObject, block: suspend () -> HostCallOutcome): HostCallOutcome {
        val startedAt = now()
        val result = block()
        val finishedAt = now()
        val record =
            HostCallRecord(
                hostCallId = "host-call-${finishedAt.toEpochMilli()}-${recordedCalls.size + 1}",
                scriptExecutionId = scriptExecutionId,
                sessionId = sessionId,
                name = name,
                argumentsJson = ScriptJson.codec.encodeToString(arguments),
                resultJson = ScriptJson.codec.encodeToString(HostCallOutcome.serializer(), result),
                startedAt = startedAt.toString(),
                finishedAt = finishedAt.toString(),
            )
        recordedCalls += record
        logStore.recordHostCall(record)
        sessionCoordinator.logEvent(
            buildString {
                append("Script host call ")
                append(name)
                append(if (result.ok) " succeeded" else " blocked")
                append(". ")
                append(result.message)
            },
        )
        return result
    }

    private fun recordSnapshot(snapshot: UiSnapshot) {
        sessionCoordinator.setLastKnownApp(snapshot.foregroundPackage)
        logStore.recordSnapshot(snapshot)
    }

    private fun String.toScrollDirection(): ScrollDirection? = when (lowercase()) {
        "up" -> ScrollDirection.Up
        "down" -> ScrollDirection.Down
        "left" -> ScrollDirection.Left
        "right" -> ScrollDirection.Right
        else -> null
    }

    private fun UiSnapshot.toPayload(): UiSnapshotPayload = UiSnapshotPayload(
        snapshotId = snapshotId,
        capturedAt = capturedAt.toString(),
        foregroundPackage = foregroundPackage,
        visibleText = visibleText,
        actionableElements = actionableElements.map { it.toPayload() },
        focusedElementId = focusedElementId,
    )

    private companion object {
        private const val POLL_INTERVAL_MS = 250L
    }
}

private fun ElementSelectorPayload.hasCriteria(): Boolean = listOf(
    ref,
    text,
    contentDescription,
    resourceId,
    role,
).any { !it.isNullOrBlank() } ||
    listOf(clickable, editable, focused, enabled, checked, selected, scrollable).any { it != null }

private fun UiElement.matches(selector: ElementSelectorPayload): Boolean {
    if (selector.ref != null && selector.ref != ref) {
        return false
    }

    if (selector.text != null) {
        val candidates = listOfNotNull(label, text, contentDescription)
        val target = selector.text
        val matchesText =
            candidates.any { candidate ->
                if (selector.textExact) {
                    candidate == target
                } else {
                    candidate.contains(target, ignoreCase = true)
                }
            }
        if (!matchesText) {
            return false
        }
    }

    if (selector.contentDescription != null &&
        !(contentDescription?.contains(selector.contentDescription, ignoreCase = true) == true)
    ) {
        return false
    }

    if (selector.resourceId != null && !(resourceId?.contains(selector.resourceId, ignoreCase = true) == true)) {
        return false
    }

    if (selector.role != null && selector.role != role) {
        return false
    }

    val stateFilters =
        listOf(
            selector.clickable to clickable,
            selector.editable to editable,
            selector.focused to focused,
            selector.enabled to enabled,
            selector.checked to checked,
            selector.selected to selected,
            selector.scrollable to scrollable,
        )
    if (stateFilters.any { (expected, actual) -> expected != null && expected != actual }) {
        return false
    }

    return true
}

private fun ActionResult.toOutcome(data: JsonObject? = null): HostCallOutcome = when (this) {
    is ActionResult.Success -> HostCallOutcome(ok = true, message = message, data = data)
    is ActionResult.Blocked -> HostCallOutcome(ok = false, message = reason, data = data)
}
