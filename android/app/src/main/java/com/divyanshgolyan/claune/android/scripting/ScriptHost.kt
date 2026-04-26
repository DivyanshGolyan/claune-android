package com.divyanshgolyan.claune.android.scripting

import com.divyanshgolyan.claune.android.data.local.SessionLogStore
import com.divyanshgolyan.claune.android.runtime.ActionResult
import com.divyanshgolyan.claune.android.runtime.ElementRef
import com.divyanshgolyan.claune.android.runtime.PhoneActuator
import com.divyanshgolyan.claune.android.runtime.PhoneObserver
import com.divyanshgolyan.claune.android.runtime.ScreenNode
import com.divyanshgolyan.claune.android.runtime.ScreenState
import com.divyanshgolyan.claune.android.runtime.ScrollDirection
import com.divyanshgolyan.claune.android.runtime.SessionCoordinator
import com.divyanshgolyan.claune.android.runtime.actionableNodes
import com.divyanshgolyan.claune.android.runtime.boundsArea
import com.divyanshgolyan.claune.android.runtime.buildScreenObservation
import com.divyanshgolyan.claune.android.runtime.focusedElementId
import com.divyanshgolyan.claune.android.runtime.isSearchLike
import com.divyanshgolyan.claune.android.runtime.visibleNodes
import com.divyanshgolyan.claune.android.runtime.visibleText
import java.time.Instant
import kotlinx.coroutines.delay
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

class ScriptHost(
    private val scriptExecutionId: String,
    private val phoneObserver: PhoneObserver,
    private val phoneActuator: PhoneActuator,
    private val installedAppRegistry: InstalledAppRegistry = EmptyInstalledAppRegistry,
    private val sessionCoordinator: SessionCoordinator,
    private val logStore: SessionLogStore,
    private val now: () -> Instant = { Instant.now() },
    private val sleeper: suspend (Long) -> Unit = { delay(it) },
) {
    private val runId: String? get() = sessionCoordinator.uiState.value.activeRunId
    private val recordedCalls = mutableListOf<HostCallRecord>()

    fun hostCalls(): List<HostCallRecord> = recordedCalls.toList()

    suspend fun observeScreen(optionsJson: String): ScreenObservationPayload {
        val options = decodeScreenObserveOptions(optionsJson)
        val mode = options.mode.toCanonicalScreenMode()
        val previous = logStore.recentScreenStates().lastOrNull()
        val screenState = phoneObserver.captureScreenState()
        recordScreenState(screenState)
        return if (options.includeDiff && mode.name == "Compact") {
            buildScreenObservation(previous, screenState).toPayload()
        } else {
            screenState.toObservationPayload(mode)
        }
    }

    suspend fun diffScreen(optionsJson: String): ScreenObservationPayload {
        val options = decodeScreenDiffOptions(optionsJson)
        val previous = options.baselineSnapshotId
            ?.let { id -> logStore.recentScreenStates().lastOrNull { it.snapshotId == id } }
            ?: logStore.recentScreenStates().lastOrNull()
        val screenState = phoneObserver.captureScreenState()
        recordScreenState(screenState)
        return buildScreenObservation(previous, screenState).toPayload()
    }

    suspend fun inspectScreen(optionsJson: String): ScreenInspectionPayload {
        val options = decodeScreenInspectOptions(optionsJson)
        val snapshot = phoneObserver.captureScreenState()
        recordScreenState(snapshot)
        return recordDataCall(
            name = "inspectScreen",
            arguments = buildJsonObject { put("options", optionsJson) },
            result = snapshot.toInspectionPayload(options),
            resultSerializer = ScreenInspectionPayload.serializer(),
        )
    }

    suspend fun findRawNodes(optionsJson: String): RawNodeSearchResultPayload {
        val options = decodeRawNodeSearchOptions(optionsJson)
        val snapshot = phoneObserver.captureScreenState()
        recordScreenState(snapshot)
        return recordDataCall(
            name = "findRawNodes",
            arguments = buildJsonObject { put("options", optionsJson) },
            result = snapshot.toRawNodeSearchResult(options),
            resultSerializer = RawNodeSearchResultPayload.serializer(),
        )
    }

    suspend fun listInstalledApps(): List<InstalledAppPayload> = recordDataCall(
        name = "listInstalledApps",
        arguments = buildJsonObject {},
        result = installedAppRegistry.listLaunchableApps(),
        resultSerializer = ListSerializer(InstalledAppPayload.serializer()),
    )

    suspend fun launchApp(packageName: String): HostCallOutcome = recordCall(
        name = "launchApp",
        arguments = buildJsonObject { put("packageName", packageName) },
    ) {
        val launchResult = installedAppRegistry.launchPackage(packageName)
        if (!launchResult.ok) {
            return@recordCall launchResult
        }
        verifyAppLaunch(packageName)
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
        val snapshot = phoneObserver.captureScreenState()
        recordScreenState(snapshot)
        val element = snapshot.actionableNodes().firstOrNull { it.ref == ref }
            ?: return@recordCall HostCallOutcome(
                ok = false,
                message = "Ref '$ref' was not found in the current snapshot.",
            )
        phoneActuator.tap(ElementRef(element.elementId)).toOutcome(
            data =
            buildJsonObject {
                put("matchedRef", element.ref)
                put("matchedElementId", element.elementId)
                put("matchedLabel", element.label)
            },
        )
    }

    suspend fun tapText(text: String, exact: Boolean): HostCallOutcome = recordCall(
        name = "tapText",
        arguments =
        buildJsonObject {
            put("text", text)
            put("exact", exact)
        },
    ) {
        val snapshot = phoneObserver.captureScreenState()
        recordScreenState(snapshot)
        val selector = ElementSelectorPayload(text = text, textExact = exact)
        val element = selectElement(snapshot, selector)
            ?: return@recordCall selectorFailure(selector, snapshot)
        phoneActuator.tap(ElementRef(element.elementId)).toOutcome(
            data =
            buildJsonObject {
                put("matchedRef", element.ref)
                put("matchedElementId", element.elementId)
                put("matchedLabel", element.label)
            },
        )
    }

    suspend fun tapPoint(x: Int, y: Int): HostCallOutcome = recordCall(
        name = "tapPoint",
        arguments =
        buildJsonObject {
            put("x", x)
            put("y", y)
        },
    ) {
        phoneActuator.tapPoint(x, y).toOutcome(
            data =
            buildJsonObject {
                put("x", x)
                put("y", y)
            },
        )
    }

    suspend fun tapBounds(boundsJson: String): HostCallOutcome = recordCall(
        name = "tapBounds",
        arguments = buildJsonObject { put("bounds", boundsJson) },
    ) {
        val bounds = decodeBounds(boundsJson)
            ?: return@recordCall HostCallOutcome(
                ok = false,
                message = "Invalid bounds. Expected [left, top, right, bottom].",
            )
        val centerX = (bounds[0] + bounds[2]) / 2
        val centerY = (bounds[1] + bounds[3]) / 2
        phoneActuator.tapPoint(centerX, centerY).toOutcome(
            data =
            buildJsonObject {
                put("bounds", boundsJson)
                put("x", centerX)
                put("y", centerY)
            },
        )
    }

    suspend fun scrollRef(ref: String, direction: String): HostCallOutcome = recordCall(
        name = "scrollRef",
        arguments =
        buildJsonObject {
            put("ref", ref)
            put("direction", direction)
        },
    ) {
        val parsedDirection = direction.toScrollDirection()
            ?: return@recordCall HostCallOutcome(
                ok = false,
                message = "Unsupported scroll direction '$direction'.",
            )
        val snapshot = phoneObserver.captureScreenState()
        recordScreenState(snapshot)
        val element = snapshot.actionableNodes().firstOrNull { it.ref == ref }
            ?: return@recordCall HostCallOutcome(
                ok = false,
                message = "Ref '$ref' was not found in the current snapshot.",
            )
        if (!element.scrollable) {
            return@recordCall HostCallOutcome(
                ok = false,
                message =
                "Matched element '${element.label.ifBlank { element.elementId }}' is not scrollable. " +
                    "Use scrollScreen(direction) unless the snapshot shows a scrollable ref.",
                data =
                buildJsonObject {
                    put("matchedRef", element.ref)
                    put("matchedElementId", element.elementId)
                    put("matchedLabel", element.label)
                },
            )
        }
        phoneActuator.scroll(ElementRef(element.elementId), parsedDirection).toOutcome(
            data =
            buildJsonObject {
                put("matchedRef", element.ref)
                put("matchedElementId", element.elementId)
                put("matchedLabel", element.label)
            },
        )
    }

    suspend fun scrollScreen(direction: String): HostCallOutcome = recordCall(
        name = "scrollScreen",
        arguments =
        buildJsonObject {
            put("direction", direction)
        },
    ) {
        val parsedDirection = direction.toScrollDirection()
            ?: return@recordCall HostCallOutcome(
                ok = false,
                message = "Unsupported scroll direction '$direction'.",
            )
        val snapshot = phoneObserver.captureScreenState()
        recordScreenState(snapshot)
        val element = snapshot.bestScrollableElement()
            ?: return@recordCall HostCallOutcome(
                ok = false,
                message = "No scrollable element was found on the current screen.",
            )
        phoneActuator.scroll(ElementRef(element.elementId), parsedDirection).toOutcome(
            data =
            buildJsonObject {
                put("matchedRef", element.ref)
                put("matchedElementId", element.elementId)
                put("matchedLabel", element.label)
            },
        )
    }

    suspend fun tapSelector(selectorJson: String): HostCallOutcome = recordCall(
        name = "tapSelector",
        arguments = buildJsonObject { put("selector", selectorJson) },
    ) {
        val selector = decodeElementSelector(selectorJson)
            ?: return@recordCall HostCallOutcome(
                ok = false,
                message = "Invalid selector JSON.",
            )
        val snapshot = phoneObserver.captureScreenState()
        recordScreenState(snapshot)
        val element = selectElement(snapshot, selector)
            ?: return@recordCall selectorFailure(selector, snapshot)
        phoneActuator.tap(ElementRef(element.elementId)).toOutcome(
            data =
            buildJsonObject {
                put("matchedRef", element.ref)
                put("matchedElementId", element.elementId)
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
        val selector = decodeElementSelector(selectorJson)
            ?: return@recordCall HostCallOutcome(
                ok = false,
                message = "Invalid selector JSON.",
            )
        val activation = focusSelectorInternal(selector, DEFAULT_FOCUS_TIMEOUT_MS)
        if (!activation.ok) {
            return@recordCall activation
        }
        activation.data?.jsonObject?.get("activatedElementId")?.jsonPrimitive?.content
            ?: return@recordCall HostCallOutcome(
                ok = false,
                message = "No editable element became available for the selector.",
                data = activation.data,
            )
        phoneActuator.typeFocused(text).toOutcome(
            data = activation.data as? JsonObject,
        )
    }

    suspend fun focusSelector(selectorJson: String, timeoutMs: Long): HostCallOutcome = recordCall(
        name = "focusSelector",
        arguments =
        buildJsonObject {
            put("selector", selectorJson)
            put("timeoutMs", timeoutMs)
        },
    ) {
        val selector = decodeElementSelector(selectorJson)
            ?: return@recordCall HostCallOutcome(
                ok = false,
                message = "Invalid selector JSON.",
            )
        focusSelectorInternal(selector, timeoutMs)
    }

    suspend fun typeIntoFocused(text: String): HostCallOutcome = recordCall(
        name = "typeIntoFocused",
        arguments =
        buildJsonObject {
            put("text", text)
        },
    ) {
        val snapshot = phoneObserver.captureScreenState()
        recordScreenState(snapshot)
        val element = snapshot.findFocusedEditableElement()
            ?: return@recordCall HostCallOutcome(
                ok = false,
                message = "No focused editable element was found on the current screen.",
            )
        phoneActuator.typeFocused(text).toOutcome(
            data = buildMatchedData(element),
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
        val selector = decodeElementSelector(selectorJson)
            ?: return@recordCall HostCallOutcome(
                ok = false,
                message = "Invalid selector JSON.",
            )
        waitForSelectorInternal(selector, timeoutMs)
    }

    private suspend fun waitForStateInternal(type: String, value: String, timeoutMs: Long): HostCallOutcome {
        val deadline = now().plusMillis(timeoutMs.coerceAtLeast(0L))
        while (true) {
            val snapshot = phoneObserver.captureScreenState()
            recordScreenState(snapshot)

            val matched =
                when (type) {
                    "package" -> snapshot.foregroundPackage == value
                    "element" -> snapshot.actionableNodes().any { it.elementId == value }
                    "text" -> snapshot.visibleText().any { it.contains(value, ignoreCase = true) }
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

    private suspend fun verifyAppLaunch(packageName: String): HostCallOutcome {
        val timeoutMs = LAUNCH_VERIFICATION_TIMEOUT_MS
        val deadline = now().plusMillis(timeoutMs)
        var lastSnapshot: ScreenState? = null

        while (true) {
            val snapshot = phoneObserver.captureScreenState()
            recordScreenState(snapshot)
            lastSnapshot = snapshot

            if (snapshot.foregroundPackage == packageName) {
                return HostCallOutcome(
                    ok = true,
                    message = "Launched package '$packageName' and verified it became foreground.",
                )
            }

            if (now().isAfter(deadline)) {
                break
            }

            sleeper(POLL_INTERVAL_MS)
        }

        val resolvedSnapshot = lastSnapshot
        val targetWindow = resolvedSnapshot.windows.firstOrNull { it.packageName == packageName }
        return HostCallOutcome(
            ok = false,
            message =
            if (targetWindow != null) {
                "Launch request for package '$packageName' was issued, but Claune is still observing " +
                    "'${resolvedSnapshot.foregroundPackage}'. The target app is present in accessibility " +
                    "windows but did not become the selected foreground root."
            } else {
                "Launch request for package '$packageName' was issued, but it never became foreground within " +
                    "${timeoutMs}ms. Android may have blocked the activity start while Claune was backgrounded."
            },
            data =
            buildJsonObject {
                put("requestedPackage", packageName)
                put("observedForegroundPackage", resolvedSnapshot.foregroundPackage)
                put("targetWindowVisible", targetWindow != null)
                resolvedSnapshot.selectedWindowReason?.let { put("selectedWindowReason", it) }
            },
        )
    }

    private suspend fun waitForSelectorInternal(selector: ElementSelectorPayload, timeoutMs: Long): HostCallOutcome {
        val deadline = now().plusMillis(timeoutMs.coerceAtLeast(0L))
        while (true) {
            val snapshot = phoneObserver.captureScreenState()
            recordScreenState(snapshot)
            val matched = selectElement(snapshot, selector)
            if (matched != null) {
                return HostCallOutcome(
                    ok = true,
                    message = "Matched selector for '${matched.label.ifBlank { matched.elementId }}'.",
                    data =
                    buildJsonObject {
                        put("matchedRef", matched.ref)
                        put("matchedElementId", matched.elementId)
                        put("matchedLabel", matched.label)
                    },
                )
            }
            if (snapshot.actionableNodes().any { it.matches(selector) } && !selector.first) {
                return selectorFailure(selector, snapshot)
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

    private suspend fun focusSelectorInternal(selector: ElementSelectorPayload, timeoutMs: Long): HostCallOutcome {
        val initialSnapshot = phoneObserver.captureScreenState()
        recordScreenState(initialSnapshot)
        val matchedElement = selectElement(initialSnapshot, selector)
            ?: initialSnapshot.visibleNodes().firstOrNull { it.matches(selector) }
            ?: return selectorFailure(selector, initialSnapshot)

        resolveEditableTarget(
            snapshot = initialSnapshot,
            matchedElement = matchedElement,
        )?.let { editable ->
            val editableLabel = editable.label.ifBlank { editable.elementId }
            return HostCallOutcome(
                ok = true,
                message = "Editable element '$editableLabel' is ready for input.",
                data = buildActivationData(matchedElement, editable),
            )
        }

        val tapResult = phoneActuator.tap(ElementRef(matchedElement.elementId))
        if (tapResult is ActionResult.Blocked) {
            return tapResult.toOutcome(data = buildMatchedData(matchedElement))
        }

        val deadline = now().plusMillis(timeoutMs.coerceAtLeast(0L))
        while (true) {
            val snapshot = phoneObserver.captureScreenState()
            recordScreenState(snapshot)
            resolveEditableTarget(
                snapshot = snapshot,
                matchedElement = matchedElement,
            )?.let { editable ->
                val matchedLabel = matchedElement.label.ifBlank { matchedElement.elementId }
                val editableLabel = editable.label.ifBlank { editable.elementId }
                return HostCallOutcome(
                    ok = true,
                    message = "Activated '$matchedLabel' and found editable element '$editableLabel'.",
                    data = buildActivationData(matchedElement, editable),
                )
            }

            if (now().isAfter(deadline)) {
                val matchedLabel = matchedElement.label.ifBlank { matchedElement.elementId }
                return HostCallOutcome(
                    ok = false,
                    message = "Timed out waiting for an editable element after activating '$matchedLabel'.",
                    data = buildMatchedData(matchedElement),
                )
            }

            sleeper(POLL_INTERVAL_MS)
        }
    }

    private fun resolveEditableTarget(snapshot: ScreenState, matchedElement: ScreenNode): ScreenNode? =
        snapshot.findFocusedEditableElement()
            ?: snapshot.actionableNodes().firstOrNull { it.editable && it.ref == matchedElement.ref }
            ?: snapshot.actionableNodes().firstOrNull { it.editable && matchedElement.isSearchLike() && it.isSearchLike() }
            ?: snapshot.actionableNodes().singleOrNull { it.editable }

    private fun ScreenState.bestScrollableElement(): ScreenNode? = actionableNodes()
        .filter { it.scrollable }
        .maxWithOrNull(
            compareBy<ScreenNode> { it.focused }
                .thenBy { it.boundsArea() }
                .thenBy { it.clickable },
        )

    private fun ScreenState.findFocusedEditableElement(): ScreenNode? =
        actionableNodes().firstOrNull { it.elementId == focusedElementId() && it.editable }
            ?: actionableNodes().firstOrNull { it.focused && it.editable }

    private fun buildMatchedData(element: ScreenNode): JsonObject = buildJsonObject {
        put("matchedRef", element.ref)
        put("matchedElementId", element.elementId)
        put("matchedLabel", element.label)
    }

    private fun buildActivationData(matchedElement: ScreenNode, editableElement: ScreenNode): JsonObject = buildJsonObject {
        put("matchedRef", matchedElement.ref)
        put("matchedElementId", matchedElement.elementId)
        put("matchedLabel", matchedElement.label)
        put("activatedElementId", editableElement.elementId)
        put("activatedRef", editableElement.ref)
        put("activatedLabel", editableElement.label)
    }

    private suspend fun recordCall(name: String, arguments: JsonObject, block: suspend () -> HostCallOutcome): HostCallOutcome {
        val startedAt = now()
        val result = block()
        val finishedAt = now()
        val record =
            HostCallRecord(
                hostCallId = "host-call-${finishedAt.toEpochMilli()}-${recordedCalls.size + 1}",
                scriptExecutionId = scriptExecutionId,
                runId = runId,
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

    private fun <T> recordDataCall(name: String, arguments: JsonObject, result: T, resultSerializer: KSerializer<T>): T {
        val startedAt = now()
        val finishedAt = now()
        val record =
            HostCallRecord(
                hostCallId = "host-call-${finishedAt.toEpochMilli()}-${recordedCalls.size + 1}",
                scriptExecutionId = scriptExecutionId,
                runId = runId,
                name = name,
                argumentsJson = ScriptJson.codec.encodeToString(arguments),
                resultJson = ScriptJson.codec.encodeToString(resultSerializer, result),
                startedAt = startedAt.toString(),
                finishedAt = finishedAt.toString(),
            )
        recordedCalls += record
        logStore.recordHostCall(record)
        sessionCoordinator.logEvent("Script host call $name succeeded.")
        return result
    }

    private fun recordScreenState(snapshot: ScreenState) {
        sessionCoordinator.setLastKnownApp(snapshot.foregroundPackage)
        logStore.recordScreenState(snapshot)
    }

    private fun decodeScreenInspectOptions(optionsJson: String): ScreenInspectOptionsPayload = runCatching {
        ScriptJson.codec.decodeFromString<ScreenInspectOptionsPayload>(optionsJson)
    }.getOrDefault(ScreenInspectOptionsPayload())

    private fun decodeRawNodeSearchOptions(optionsJson: String): RawNodeSearchOptionsPayload = runCatching {
        ScriptJson.codec.decodeFromString<RawNodeSearchOptionsPayload>(optionsJson)
    }.getOrDefault(RawNodeSearchOptionsPayload(pattern = ""))

    private fun decodeScreenObserveOptions(optionsJson: String): ScreenObserveOptionsPayload = runCatching {
        ScriptJson.codec.decodeFromString<ScreenObserveOptionsPayload>(optionsJson)
    }.getOrDefault(ScreenObserveOptionsPayload())

    private fun decodeScreenDiffOptions(optionsJson: String): ScreenDiffOptionsPayload = runCatching {
        ScriptJson.codec.decodeFromString<ScreenDiffOptionsPayload>(optionsJson)
    }.getOrDefault(ScreenDiffOptionsPayload())

    private fun decodeBounds(boundsJson: String): List<Int>? = runCatching {
        ScriptJson.codec.parseToJsonElement(boundsJson).jsonArray.map { it.jsonPrimitive.int }
    }.getOrNull()?.takeIf { bounds ->
        bounds.size == 4 && bounds[2] > bounds[0] && bounds[3] > bounds[1]
    }

    private fun String.toScrollDirection(): ScrollDirection? = when (lowercase()) {
        "up" -> ScrollDirection.Up
        "down" -> ScrollDirection.Down
        "left" -> ScrollDirection.Left
        "right" -> ScrollDirection.Right
        else -> null
    }

    private companion object {
        private const val POLL_INTERVAL_MS = 250L
        private const val DEFAULT_FOCUS_TIMEOUT_MS = 1500L
        private const val LAUNCH_VERIFICATION_TIMEOUT_MS = 2500L
    }
}

private fun ActionResult.toOutcome(data: JsonObject? = null): HostCallOutcome = when (this) {
    is ActionResult.Success -> HostCallOutcome(ok = true, message = message, data = data)
    is ActionResult.Blocked -> HostCallOutcome(ok = false, message = reason, data = data)
}
