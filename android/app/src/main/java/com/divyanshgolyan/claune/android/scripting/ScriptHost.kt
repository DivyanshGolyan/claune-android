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

    private fun ActionResult.toOutcome(): HostCallOutcome = when (this) {
        is ActionResult.Success -> HostCallOutcome(ok = true, message = message)
        is ActionResult.Blocked -> HostCallOutcome(ok = false, message = reason)
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

    private fun UiElement.toPayload(): UiElementPayload = UiElementPayload(
        id = id,
        role = role,
        label = label,
        clickable = clickable,
        editable = editable,
        focused = focused,
        bounds = bounds,
    )

    private companion object {
        private const val POLL_INTERVAL_MS = 250L
    }
}
