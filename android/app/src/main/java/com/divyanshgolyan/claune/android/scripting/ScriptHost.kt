package com.divyanshgolyan.claune.android.scripting

import com.divyanshgolyan.claune.android.data.local.PerfEventRecord
import com.divyanshgolyan.claune.android.data.local.PerfPhaseRecord
import com.divyanshgolyan.claune.android.data.local.PerfTelemetry
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
import com.divyanshgolyan.claune.android.runtime.centerPoint
import com.divyanshgolyan.claune.android.runtime.elapsedMs
import com.divyanshgolyan.claune.android.runtime.focusedElementId
import com.divyanshgolyan.claune.android.runtime.isSearchLike
import com.divyanshgolyan.claune.android.runtime.visibleText
import java.time.Instant
import kotlinx.coroutines.delay
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
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

    fun recordPerfEvent(
        name: String,
        scope: String,
        durationMs: Long,
        attrs: Map<String, String> = emptyMap(),
        phases: List<PerfPhaseRecord> = emptyList(),
    ) {
        logStore.recordPerfEvent(
            PerfEventRecord(
                recordedAt = now().toString(),
                runId = runId,
                scriptExecutionId = scriptExecutionId,
                scope = scope,
                name = name,
                durationMs = durationMs,
                attrs = attrs,
                phases = phases,
            ),
        )
    }

    suspend fun observeScreen(optionsJson: String): ScreenObservationPayload {
        val options = decodeScreenObserveOptions(optionsJson)
        val previous = logStore.recentScreenStates().lastOrNull()
        val screenState = phoneObserver.captureScreenState()
        recordScreenState(screenState)
        val projectionStarted = System.nanoTime()
        var projectionProfiler: ProjectionProfiler? = null
        val payload = if (options.mode.equals(SCREEN_MODE_INTERACTIONS, ignoreCase = true)) {
            ProjectionProfiler().also { profiler ->
                projectionProfiler = profiler
            }.let { profiler -> screenState.toInteractionObservationPayload(profiler) }
        } else {
            val mode = options.mode.toCanonicalScreenMode()
            if (options.includeDiff && mode.name == "Compact") {
                buildScreenObservation(previous, screenState).toPayload()
            } else {
                screenState.toObservationPayload(mode)
            }
        }
        recordObservationProjectionPerf(PerfTelemetry.OBSERVE_SCREEN_PROJECT, screenState, payload, projectionStarted, projectionProfiler)
        return payload
    }

    suspend fun diffScreen(optionsJson: String): ScreenObservationPayload {
        val options = decodeScreenDiffOptions(optionsJson)
        val previous = options.baselineSnapshotId
            ?.let { id -> logStore.recentScreenStates().lastOrNull { it.snapshotId == id } }
            ?: logStore.recentScreenStates().lastOrNull()
        val screenState = phoneObserver.captureScreenState()
        recordScreenState(screenState)
        val projectionStarted = System.nanoTime()
        val payload = buildScreenObservation(previous, screenState).toPayload()
        recordObservationProjectionPerf(PerfTelemetry.DIFF_SCREEN_PROJECT, screenState, payload, projectionStarted)
        return payload
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
        val currentSnapshot = phoneObserver.captureScreenState()
        recordScreenState(currentSnapshot)
        if (currentSnapshot.foregroundPackage == packageName) {
            return@recordCall HostCallOutcome(
                ok = true,
                message = "Package '$packageName' is already foreground.",
                data = buildJsonObject {
                    putDeviceSnapshotData(currentSnapshot)
                    putTraceTags("launch_already_foreground")
                },
            )
        }
        val launchResult = installedAppRegistry.launchPackage(packageName)
        if (!launchResult.ok) {
            return@recordCall launchResult
        }
        verifyAppLaunch(packageName)
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
        phoneActuator.tap(ElementRef(element.elementId)).toHostCallOutcome(
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
        phoneActuator.tapPoint(x, y).toHostCallOutcome(
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
                errorCode = "invalid_bounds",
            )
        val center = bounds.centerPoint()
        phoneActuator.tapPoint(center[0], center[1]).toHostCallOutcome(
            data =
            buildJsonObject {
                put("bounds", boundsJson)
                put("x", center[0])
                put("y", center[1])
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
        phoneActuator.scroll(ElementRef(element.elementId), parsedDirection).toHostCallOutcome(
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
        phoneActuator.scroll(ElementRef(element.elementId), parsedDirection).toHostCallOutcome(
            data =
            buildJsonObject {
                put("matchedRef", element.ref)
                put("matchedElementId", element.elementId)
                put("matchedLabel", element.label)
            },
        )
    }

    suspend fun locatorQuery(specJson: String): HostCallOutcome = recordCall(
        name = "locatorQuery",
        arguments = buildJsonObject { put("spec", specJson) },
    ) {
        val spec = decodeLocatorSpec(specJson)
            ?: return@recordCall invalidLocatorFailure()
        val snapshot = phoneObserver.captureScreenState()
        recordScreenState(snapshot)
        val candidates = queryLocator(snapshot, spec)
        HostCallOutcome(
            ok = true,
            message = "Locator matched ${candidates.size} visible element(s).",
            data = buildJsonObject {
                locatorQueryData(spec, candidates).forEach { (key, value) -> put(key, value) }
                putTraceTags("supported_discovery")
            },
        )
    }

    suspend fun locatorCount(specJson: String): HostCallOutcome = recordCall(
        name = "locatorCount",
        arguments = buildJsonObject { put("spec", specJson) },
    ) {
        val spec = decodeLocatorSpec(specJson)
            ?: return@recordCall invalidLocatorFailure()
        val snapshot = phoneObserver.captureScreenState()
        recordScreenState(snapshot)
        val count = queryLocator(snapshot, spec).size
        HostCallOutcome(
            ok = true,
            message = "Locator matched $count visible element(s).",
            data = buildJsonObject {
                locatorCountData(spec, count).forEach { (key, value) -> put(key, value) }
                putTraceTags("supported_discovery")
            },
        )
    }

    suspend fun locatorDescribe(specJson: String, optionsJson: String): HostCallOutcome = recordCall(
        name = "locatorDescribe",
        arguments = buildJsonObject {
            put("spec", specJson)
            put("options", optionsJson)
        },
    ) {
        val spec = decodeLocatorSpec(specJson)
            ?: return@recordCall invalidLocatorFailure()
        val options = decodeLocatorDescribeOptions(optionsJson)
        val snapshot = phoneObserver.captureScreenState()
        recordScreenState(snapshot)
        val candidates = queryLocator(snapshot, spec)
        HostCallOutcome(
            ok = true,
            message = "Locator described ${candidates.size} visible candidate(s).",
            data = buildJsonObject {
                locatorDescribeData(spec, candidates, options.limit).forEach { (key, value) -> put(key, value) }
                putDeviceSnapshotData(snapshot)
                putTraceTags("supported_discovery")
            },
        )
    }

    suspend fun locatorIsVisible(specJson: String): HostCallOutcome = recordCall(
        name = "locatorIsVisible",
        arguments = buildJsonObject { put("spec", specJson) },
    ) {
        val spec = decodeLocatorSpec(specJson)
            ?: return@recordCall invalidLocatorFailure()
        val snapshot = phoneObserver.captureScreenState()
        recordScreenState(snapshot)
        val candidates = queryLocator(snapshot, spec)
        val narrowed = spec.index?.let { index -> candidates.getOrNull(index)?.let(::listOf).orEmpty() } ?: candidates
        HostCallOutcome(
            ok = true,
            message = "Locator visibility checked.",
            data = buildJsonObject {
                put("visible", narrowed.any { it.node.locatorVisible() })
                put("count", candidates.size)
                putTraceTags("supported_discovery")
            },
        )
    }

    suspend fun locatorIsHidden(specJson: String): HostCallOutcome = recordCall(
        name = "locatorIsHidden",
        arguments = buildJsonObject { put("spec", specJson) },
    ) {
        val spec = decodeLocatorSpec(specJson)
            ?: return@recordCall invalidLocatorFailure()
        val snapshot = phoneObserver.captureScreenState()
        recordScreenState(snapshot)
        val candidates = queryLocator(snapshot, spec)
        val narrowed = spec.index?.let { index -> candidates.getOrNull(index)?.let(::listOf).orEmpty() } ?: candidates
        HostCallOutcome(
            ok = true,
            message = "Locator hidden state checked.",
            data = buildJsonObject {
                put("hidden", narrowed.none { it.node.locatorVisible() })
                put("count", candidates.size)
                putTraceTags("supported_discovery")
            },
        )
    }

    suspend fun locatorClick(specJson: String, optionsJson: String): HostCallOutcome = recordCall(
        name = "locatorClick",
        arguments = buildJsonObject {
            put("spec", specJson)
            put("options", optionsJson)
        },
    ) {
        val spec = decodeLocatorSpec(specJson)
            ?: return@recordCall invalidLocatorFailure()
        val options = decodeLocatorOptions(optionsJson)
        val target = waitForStrictLocator(
            spec = spec,
            timeoutMs = options.timeoutMs,
            state = LOCATOR_STATE_ACTIONABLE,
            force = options.force,
        )
        if (!target.ok) return@recordCall target
        val elementId = target.data?.jsonObject?.get("targetElementId")?.jsonPrimitive?.content
            ?: target.data?.jsonObject?.get("matchedElementId")?.jsonPrimitive?.content
            ?: return@recordCall HostCallOutcome(
                ok = false,
                message = "Locator resolved without a matched element id.",
                data = target.data,
                errorCode = "not_actionable",
            )
        val tapResult = phoneActuator.tap(ElementRef(elementId))
        tapResult.toHostCallOutcome(
            data =
            buildJsonObject {
                target.data?.jsonObject?.forEach { (key, value) -> put(key, value) }
                put("force", options.force)
            },
            errorCode = "not_actionable",
        )
    }

    suspend fun locatorFill(specJson: String, text: String, optionsJson: String): HostCallOutcome = recordCall(
        name = "locatorFill",
        arguments = buildJsonObject {
            put("spec", specJson)
            put("text", text)
            put("options", optionsJson)
        },
    ) {
        val spec = decodeLocatorSpec(specJson)
            ?: return@recordCall invalidLocatorFailure()
        val options = decodeLocatorOptions(optionsJson)
        val deadline = now().plusMillis(options.timeoutMs.coerceAtLeast(0L))
        while (true) {
            val snapshot = phoneObserver.captureScreenState()
            recordScreenState(snapshot)
            val candidates = queryLocator(snapshot, spec)
            val narrowed = spec.index
                ?.let { index -> candidates.getOrNull(index)?.let(::listOf).orEmpty() }
                ?: candidates.collapseFillIntentCandidates()
            if (spec.index == null && narrowed.size > 1) {
                return@recordCall HostCallOutcome(
                    ok = false,
                    message = "Locator matched ${narrowed.size} elements. Refine it or use first()/nth() deliberately.",
                    data = locatorQueryData(spec, candidates),
                    errorCode = "ambiguous_match",
                )
            }
            narrowed.singleOrNull()?.let { candidate ->
                if (!candidate.actionNode.enabled) {
                    return@recordCall HostCallOutcome(
                        ok = false,
                        message = "Locator target is disabled.",
                        data = buildMatchedData(candidate),
                        errorCode = "disabled",
                    )
                }
                return@recordCall fillResolved(
                    initialSnapshot = snapshot,
                    matchedElement = candidate.actionNode,
                    text = text,
                    timeoutMs = remainingMillis(deadline),
                )
            }
            if (now().isAfter(deadline)) {
                return@recordCall HostCallOutcome(
                    ok = false,
                    message = "Timed out waiting for fill locator after ${options.timeoutMs.coerceAtLeast(0L)}ms.",
                    data = locatorQueryData(spec, candidates),
                    errorCode = if (candidates.size > 1) "ambiguous_match" else "timeout",
                )
            }
            sleeper(POLL_INTERVAL_MS)
        }
        @Suppress("UNREACHABLE_CODE")
        HostCallOutcome(ok = false, message = "Locator fill failed unexpectedly.", errorCode = "timeout")
    }

    suspend fun locatorWaitFor(specJson: String, optionsJson: String): HostCallOutcome = recordCall(
        name = "locatorWaitFor",
        arguments = buildJsonObject {
            put("spec", specJson)
            put("options", optionsJson)
        },
    ) {
        val spec = decodeLocatorSpec(specJson)
            ?: return@recordCall invalidLocatorFailure()
        val options = decodeLocatorOptions(optionsJson)
        waitForStrictLocator(
            spec = spec,
            timeoutMs = options.timeoutMs,
            state = options.state,
            force = options.force,
        )
    }

    suspend fun locatorAssert(specJson: String, assertionJson: String): HostCallOutcome = recordCall(
        name = "locatorAssert",
        arguments = buildJsonObject {
            put("spec", specJson)
            put("assertion", assertionJson)
        },
    ) {
        val spec = decodeLocatorSpec(specJson)
            ?: return@recordCall invalidLocatorFailure()
        val assertion = decodeLocatorAssertion(assertionJson)
            ?: return@recordCall HostCallOutcome(
                ok = false,
                message = "Invalid locator assertion.",
                errorCode = "invalid_assertion",
            )
        invalidLocatorAssertion(assertion)?.let { failure -> return@recordCall failure }
        waitForLocatorAssertion(spec, assertion)
    }

    suspend fun locatorTextContent(specJson: String, optionsJson: String): HostCallOutcome = recordCall(
        name = "locatorTextContent",
        arguments = buildJsonObject {
            put("spec", specJson)
            put("options", optionsJson)
        },
    ) {
        val spec = decodeLocatorSpec(specJson)
            ?: return@recordCall invalidLocatorFailure()
        val options = decodeLocatorOptions(optionsJson)
        val target = waitForStrictLocator(
            spec = spec,
            timeoutMs = options.timeoutMs,
            state = LOCATOR_STATE_VISIBLE,
            force = options.force,
        )
        if (!target.ok) return@recordCall target
        val text = target.data?.jsonObject?.get("textContent")?.jsonPrimitive?.content.orEmpty()
        HostCallOutcome(
            ok = true,
            message = "Locator text content extracted.",
            data = buildJsonObject {
                put("text", text)
                target.data?.jsonObject?.forEach { (key, value) -> put(key, value) }
            },
        )
    }

    suspend fun locatorAllTextContents(specJson: String): HostCallOutcome = recordCall(
        name = "locatorAllTextContents",
        arguments = buildJsonObject { put("spec", specJson) },
    ) {
        val spec = decodeLocatorSpec(specJson)
            ?: return@recordCall invalidLocatorFailure()
        val snapshot = phoneObserver.captureScreenState()
        recordScreenState(snapshot)
        val matchedTexts = queryLocator(snapshot, spec)
            .mapNotNull { it.node.locatorTextContent().takeIf(String::isNotBlank) }
        val texts = matchedTexts.take(100)
        HostCallOutcome(
            ok = true,
            message = "Locator matched ${texts.size} text value(s).",
            data = buildJsonObject {
                put("texts", buildJsonArray { texts.forEach { add(JsonPrimitive(it)) } })
                put("truncated", matchedTexts.size > texts.size)
                putTraceTags("supported_discovery")
            },
        )
    }

    suspend fun locatorPress(specJson: String, key: String, optionsJson: String): HostCallOutcome = recordCall(
        name = "locatorPress",
        arguments = buildJsonObject {
            put("spec", specJson)
            put("key", key)
            put("options", optionsJson)
        },
    ) {
        if (!key.equals("Enter", ignoreCase = true)) {
            return@recordCall HostCallOutcome(
                ok = false,
                message = "Only locator.press(\"Enter\") is supported in this spike.",
                errorCode = "unsupported_key",
            )
        }
        val spec = decodeLocatorSpec(specJson)
            ?: return@recordCall invalidLocatorFailure()
        val options = decodeLocatorOptions(optionsJson)
        val target = waitForStrictLocator(
            spec = spec,
            timeoutMs = options.timeoutMs,
            state = LOCATOR_STATE_VISIBLE,
            force = options.force,
        )
        val fallbackSnapshot = if (target.ok) null else phoneObserver.captureScreenState().also(::recordScreenState)
        val fallbackEditable = fallbackSnapshot?.findFocusedEditableElement()
        if (!target.ok && fallbackEditable == null) {
            return@recordCall HostCallOutcome(
                ok = false,
                message = target.message,
                data = buildJsonObject {
                    target.data?.jsonObject?.forEach { (dataKey, value) -> put(dataKey, value) }
                    fallbackSnapshot?.let { putInputDiagnostics(it, null) }
                },
                errorCode = target.errorCode,
            )
        }
        val elementId = target.data?.jsonObject?.get("targetElementId")?.jsonPrimitive?.content
            ?: target.data?.jsonObject?.get("matchedElementId")?.jsonPrimitive?.content
            ?: fallbackEditable?.elementId
            ?: return@recordCall HostCallOutcome(
                ok = false,
                message = "Locator resolved without a target element id.",
                data = target.data,
                errorCode = "not_actionable",
            )
        val pressResult = phoneActuator.pressEnter(ElementRef(elementId))
        pressResult.toHostCallOutcome(
            data = buildJsonObject {
                target.data?.jsonObject?.forEach { (dataKey, value) -> put(dataKey, value) }
                fallbackEditable?.let { editable ->
                    put("focusedEditableFallback", compactNodeData(editable))
                    putTraceTags("focused_editable_fallback")
                }
                put("key", "Enter")
            },
            errorCode = "unsupported_key",
        )
    }

    suspend fun deviceCurrent(): HostCallOutcome = recordCall(
        name = "deviceCurrent",
        arguments = buildJsonObject {},
    ) {
        val snapshot = phoneObserver.captureScreenState()
        recordScreenState(snapshot)
        HostCallOutcome(
            ok = true,
            message = "Current device state captured.",
            data = buildJsonObject { putDeviceSnapshotData(snapshot) },
        )
    }

    suspend fun pressBack(): HostCallOutcome = recordCall(
        name = "pressBack",
        arguments = buildJsonObject {},
    ) {
        phoneActuator.pressBack().toHostCallOutcome()
    }

    suspend fun pressHome(): HostCallOutcome = recordCall(
        name = "pressHome",
        arguments = buildJsonObject {},
    ) {
        phoneActuator.pressHome().toHostCallOutcome()
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
        if (type !in SUPPORTED_WAIT_STATE_TYPES) {
            return HostCallOutcome(
                ok = false,
                message = "Unsupported waitForState type '$type'.",
                errorCode = "invalid_wait_state",
            )
        }
        val matcher = WaitValueMatcher.from(value)
        val deadline = now().plusMillis(timeoutMs.coerceAtLeast(0L))
        while (true) {
            val snapshot = phoneObserver.captureScreenState()
            recordScreenState(snapshot)

            val matched =
                when (type) {
                    "package" -> matcher.matchesPackage(snapshot.foregroundPackage)
                    "element" -> snapshot.actionableNodes().any { matcher.matchesElementId(it.elementId) }
                    "text" -> snapshot.visibleText().any(matcher::matchesText)
                    else -> false
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
                    errorCode = "timeout",
                )
            }

            sleeper(POLL_INTERVAL_MS)
        }
    }

    private suspend fun verifyAppLaunch(packageName: String): HostCallOutcome {
        val timeoutMs = LAUNCH_VERIFICATION_TIMEOUT_MS
        val deadline = now().plusMillis(timeoutMs.coerceAtLeast(0L))
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

    private fun resolveEditableTarget(snapshot: ScreenState, matchedElement: ScreenNode): ScreenNode? =
        snapshot.findFocusedEditableElement()
            ?: snapshot.actionableNodes().firstOrNull { it.editable && it.ref == matchedElement.ref }
            ?: snapshot.actionableNodes().firstOrNull { it.editable && matchedElement.isSearchLike() && it.isSearchLike() }
            ?: snapshot.actionableNodes().singleOrNull { it.editable }

    private suspend fun fillResolved(
        initialSnapshot: ScreenState,
        matchedElement: ScreenNode,
        text: String,
        timeoutMs: Long,
    ): HostCallOutcome {
        resolveEditableTarget(initialSnapshot, matchedElement)?.let { editable ->
            return typeResolvedEditable(
                matchedElement = matchedElement,
                editableElement = editable,
                text = text,
                method = "set_text_existing_editable",
            )
        }

        val tapResult = phoneActuator.tap(ElementRef(matchedElement.elementId))
        if (tapResult is ActionResult.Blocked) {
            return tapResult.toHostCallOutcome(
                data = buildFillData(matchedElement = matchedElement, method = "activate_target", typed = false),
                errorCode = "not_actionable",
            )
        }

        val deadline = now().plusMillis(timeoutMs.coerceAtLeast(0L))
        var triedFocusedTyping = false
        while (true) {
            val snapshot = phoneObserver.captureScreenState()
            recordScreenState(snapshot)
            resolveEditableTarget(snapshot, matchedElement)?.let { editable ->
                return typeResolvedEditable(
                    matchedElement = matchedElement,
                    editableElement = editable,
                    text = text,
                    method = "activate_then_set_text",
                    visibleTextAfter = snapshot.visibleText().take(20),
                )
            }
            if (!triedFocusedTyping && matchedElement.looksLikeTextInputSurface()) {
                triedFocusedTyping = true
                val focusedTypeResult = phoneActuator.typeFocused(text)
                if (focusedTypeResult is ActionResult.Success) {
                    val afterSnapshot = phoneObserver.captureScreenState().also(::recordScreenState)
                    return focusedTypeResult.toHostCallOutcome(
                        data = buildFillData(
                            matchedElement = matchedElement,
                            method = "activate_then_type_focused",
                            typed = true,
                            visibleTextAfter = afterSnapshot.visibleText().take(20),
                            afterSnapshot = afterSnapshot,
                            expectedText = text,
                        ),
                        errorCode = "input_rejected",
                    )
                }
            }

            if (now().isAfter(deadline)) {
                val matchedLabel = matchedElement.label.ifBlank { matchedElement.elementId }
                return HostCallOutcome(
                    ok = false,
                    message = "Timed out waiting for an editable element after activating '$matchedLabel'.",
                    data = buildFillData(
                        matchedElement = matchedElement,
                        method = "activate_then_set_text",
                        typed = false,
                        afterSnapshot = snapshot,
                        expectedText = text,
                    ),
                    errorCode = "not_editable",
                )
            }

            sleeper(POLL_INTERVAL_MS)
        }
    }

    private fun List<LocatorCandidate>.collapseFillIntentCandidates(): List<LocatorCandidate> {
        if (size <= 1) return this
        if (!all { it.node.looksLikeTextInputSurface() || it.actionNode.looksLikeTextInputSurface() }) return this
        val comparableLabels = map { it.node.label.normalizedFillIntentLabel() }
            .filter { it.isNotBlank() }
            .distinct()
        if (comparableLabels.size > 1) return this
        val topCandidate = maxWithOrNull(
            compareBy<LocatorCandidate> { it.node.editable }
                .thenBy { it.actionNode.clickable }
                .thenBy { it.score }
                .thenByDescending { it.node.boundsArea() },
        ) ?: return this
        val topBounds = topCandidate.actionNode.bounds
        return if (all { it.actionNode.bounds.visuallyOverlaps(topBounds) }) {
            listOf(topCandidate)
        } else {
            this
        }
    }

    private suspend fun waitForStrictLocator(spec: LocatorSpecPayload, timeoutMs: Long, state: String, force: Boolean): HostCallOutcome {
        val deadline = now().plusMillis(timeoutMs.coerceAtLeast(0L))
        var lastFailure: HostCallOutcome? = null
        while (true) {
            val snapshot = phoneObserver.captureScreenState()
            recordScreenState(snapshot)
            val candidates = queryLocator(snapshot, spec)
            val narrowed = spec.index?.let { index -> candidates.getOrNull(index)?.let(::listOf).orEmpty() } ?: candidates

            when (state) {
                LOCATOR_STATE_HIDDEN -> if (narrowed.isEmpty()) {
                    return HostCallOutcome(
                        ok = true,
                        message = "Locator is hidden.",
                        data = locatorQueryData(spec, candidates),
                    )
                }
                LOCATOR_STATE_VISIBLE -> {
                    if (spec.index == null && narrowed.size > 1) {
                        return strictLocatorStateFailure(spec, candidates, narrowed, state)
                    }
                    val single = narrowed.singleOrNull()
                    if (single != null && single.node.locatorVisible()) {
                        return HostCallOutcome(
                            ok = true,
                            message = "Locator resolved to visible element '${single.node.label.ifBlank { single.node.elementId }}'.",
                            data = buildMatchedData(single),
                        )
                    }
                    lastFailure = strictLocatorStateFailure(spec, candidates, narrowed, state)
                }
                LOCATOR_STATE_ACTIONABLE -> {
                    if (spec.index == null && narrowed.size > 1) {
                        return strictLocatorStateFailure(spec, candidates, narrowed, state)
                    }
                    val single = narrowed.singleOrNull()
                    if (single != null) {
                        val node = single.actionNode
                        when {
                            !node.locatorVisible() ->
                                lastFailure = HostCallOutcome(
                                    ok = false,
                                    message = "Locator target is not visible.",
                                    data = buildMatchedData(single),
                                    errorCode = "not_visible",
                                )
                            !node.enabled ->
                                lastFailure = HostCallOutcome(
                                    ok = false,
                                    message = "Locator target is disabled.",
                                    data = buildMatchedData(single),
                                    errorCode = "disabled",
                                )
                            node.isLocatorActionable(force) ->
                                return HostCallOutcome(
                                    ok = true,
                                    message = "Locator resolved to actionable element '${node.label.ifBlank { node.elementId }}'.",
                                    data = buildMatchedData(single),
                                )
                            else ->
                                lastFailure = HostCallOutcome(
                                    ok = false,
                                    message = "Locator target is visible but not actionable. ${node.clickabilityReason}",
                                    data = buildMatchedData(single),
                                    errorCode = "not_actionable",
                                )
                        }
                    } else {
                        lastFailure = strictLocatorStateFailure(spec, candidates, narrowed, state)
                    }
                }
                else ->
                    return HostCallOutcome(
                        ok = false,
                        message = "Unsupported locator wait state '$state'.",
                        errorCode = "invalid_wait_state",
                    )
            }

            if (now().isAfter(deadline)) {
                val failure = lastFailure ?: locatorFailure(spec, snapshot)
                return HostCallOutcome(
                    ok = false,
                    message = "Timed out waiting for locator state '$state' after ${timeoutMs.coerceAtLeast(0L)}ms. ${failure.message}",
                    data = failure.data,
                    errorCode = failure.errorCode ?: "timeout",
                )
            }

            sleeper(POLL_INTERVAL_MS)
        }
    }

    private fun strictLocatorStateFailure(
        spec: LocatorSpecPayload,
        candidates: List<LocatorCandidate>,
        narrowed: List<LocatorCandidate>,
        state: String,
    ): HostCallOutcome = when {
        narrowed.isEmpty() -> locatorFailure(spec, candidates)
        narrowed.size > 1 -> HostCallOutcome(
            ok = false,
            message = "Locator matched ${narrowed.size} elements while waiting for $state.",
            data = locatorQueryData(spec, narrowed),
            errorCode = "ambiguous_match",
        )
        else -> HostCallOutcome(
            ok = false,
            message = "Locator target did not reach state '$state'.",
            data = buildMatchedData(narrowed.single()),
            errorCode = "timeout",
        )
    }

    private suspend fun waitForLocatorAssertion(spec: LocatorSpecPayload, assertion: LocatorAssertionPayload): HostCallOutcome {
        val deadline = now().plusMillis(assertion.timeoutMs.coerceAtLeast(0L))
        var lastCandidates: List<LocatorCandidate> = emptyList()
        while (true) {
            val snapshot = phoneObserver.captureScreenState()
            recordScreenState(snapshot)
            val candidates = queryLocator(snapshot, spec)
            val narrowed = spec.index?.let { index -> candidates.getOrNull(index)?.let(::listOf).orEmpty() } ?: candidates
            lastCandidates = candidates

            when (assertion.matcher) {
                LOCATOR_ASSERTION_VISIBLE -> {
                    if (spec.index == null && narrowed.size > 1) {
                        return HostCallOutcome(
                            ok = false,
                            message = "Locator matched ${narrowed.size} elements while waiting for assertion '${assertion.matcher}'.",
                            data = locatorQueryData(spec, narrowed),
                            errorCode = "ambiguous_match",
                        )
                    }
                    narrowed.singleOrNull()?.takeIf { it.node.locatorVisible() }?.let {
                        return HostCallOutcome(ok = true, message = "Expected locator is visible.", data = buildMatchedData(it))
                    }
                }
                LOCATOR_ASSERTION_HIDDEN -> if (narrowed.isEmpty()) {
                    return HostCallOutcome(ok = true, message = "Expected locator is hidden.", data = locatorQueryData(spec, candidates))
                }
                LOCATOR_ASSERTION_COUNT -> if (candidates.size == assertion.expectedCount) {
                    return HostCallOutcome(
                        ok = true,
                        message = "Expected locator count ${assertion.expectedCount} matched.",
                        data = locatorQueryData(spec, candidates),
                    )
                }
                LOCATOR_ASSERTION_TEXT -> {
                    if (spec.index == null && narrowed.size > 1) {
                        return HostCallOutcome(
                            ok = false,
                            message = "Locator matched ${narrowed.size} elements while waiting for assertion '${assertion.matcher}'.",
                            data = locatorQueryData(spec, narrowed),
                            errorCode = "ambiguous_match",
                        )
                    }
                    narrowed.singleOrNull()?.takeIf { candidate ->
                        candidate.node.assertionTextMatches(assertion)
                    }?.let {
                        return HostCallOutcome(ok = true, message = "Expected locator text matched.", data = buildMatchedData(it))
                    }
                }
                else ->
                    return HostCallOutcome(
                        ok = false,
                        message = "Unsupported locator assertion '${assertion.matcher}'.",
                        data = locatorQueryData(spec, candidates),
                        errorCode = "invalid_assertion",
                    )
            }

            if (now().isAfter(deadline)) {
                return HostCallOutcome(
                    ok = false,
                    message = "Timed out waiting for assertion '${assertion.matcher}' after ${assertion.timeoutMs.coerceAtLeast(0L)}ms.",
                    data = locatorQueryData(spec, lastCandidates),
                    errorCode = "timeout",
                )
            }

            sleeper(POLL_INTERVAL_MS)
        }
    }

    private fun ScreenNode.assertionTextMatches(assertion: LocatorAssertionPayload): Boolean {
        val matcher = LocatorTextMatcher.from(
            text = assertion.expectedText,
            exact = false,
            pattern = assertion.expectedPattern,
            flags = assertion.expectedFlags,
        ) ?: return false
        return matcher.matches(listOf(label, text, contentDescription))
    }

    private fun invalidLocatorAssertion(assertion: LocatorAssertionPayload): HostCallOutcome? = when (assertion.matcher) {
        LOCATOR_ASSERTION_VISIBLE, LOCATOR_ASSERTION_HIDDEN -> null
        LOCATOR_ASSERTION_COUNT -> if (assertion.expectedCount == null || assertion.expectedCount < 0) {
            HostCallOutcome(
                ok = false,
                message = "toHaveCount requires a non-negative expected count.",
                errorCode = "invalid_assertion",
            )
        } else {
            null
        }
        LOCATOR_ASSERTION_TEXT -> if (
            LocatorTextMatcher.from(
                text = assertion.expectedText,
                exact = false,
                pattern = assertion.expectedPattern,
                flags = assertion.expectedFlags,
            ) == null
        ) {
            HostCallOutcome(
                ok = false,
                message = "toHaveText requires expectedText or a valid expectedPattern.",
                errorCode = "invalid_assertion",
            )
        } else {
            null
        }
        else -> HostCallOutcome(
            ok = false,
            message = "Unsupported locator assertion '${assertion.matcher}'.",
            errorCode = "invalid_assertion",
        )
    }

    private fun remainingMillis(deadline: Instant): Long = java.time.Duration.between(now(), deadline).toMillis().coerceAtLeast(0L)

    private suspend fun typeResolvedEditable(
        matchedElement: ScreenNode,
        editableElement: ScreenNode,
        text: String,
        method: String,
        visibleTextAfter: List<String> = emptyList(),
    ): HostCallOutcome {
        val typeResult = phoneActuator.type(ElementRef(editableElement.elementId), text)
        val afterSnapshot = if (typeResult is ActionResult.Success) {
            phoneObserver.captureScreenState().also(::recordScreenState)
        } else {
            null
        }
        return typeResult.toHostCallOutcome(
            data =
            buildFillData(
                matchedElement = matchedElement,
                editableElement = editableElement,
                method = method,
                typed = typeResult is ActionResult.Success,
                visibleTextAfter = afterSnapshot?.visibleText()?.take(20) ?: visibleTextAfter,
                afterSnapshot = afterSnapshot,
                expectedText = text,
            ),
            errorCode = "input_rejected",
        )
    }

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
        put("textContent", element.locatorTextContent())
    }

    private fun buildMatchedData(candidate: LocatorCandidate): JsonObject = buildJsonObject {
        put("matchedRef", candidate.node.ref)
        put("matchedElementId", candidate.node.elementId)
        put("matchedLabel", candidate.node.label)
        put("targetRef", candidate.actionNode.ref)
        put("targetElementId", candidate.actionNode.elementId)
        put("targetLabel", candidate.actionNode.label)
        put("textContent", candidate.node.locatorTextContent())
    }

    private fun ScreenNode.locatorTextContent(): String {
        val values = linkedSetOf<String>()

        fun visit(node: ScreenNode) {
            listOfNotNull(node.text, node.label, node.contentDescription)
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .forEach(values::add)
            node.children.forEach(::visit)
        }

        visit(this)
        return values.joinToString(separator = " | ")
    }

    private fun buildFillData(
        matchedElement: ScreenNode,
        editableElement: ScreenNode? = null,
        method: String,
        typed: Boolean,
        visibleTextAfter: List<String> = emptyList(),
        afterSnapshot: ScreenState? = null,
        expectedText: String? = null,
    ): JsonObject = buildJsonObject {
        put("matchedRef", matchedElement.ref)
        put("matchedElementId", matchedElement.elementId)
        put("matchedLabel", matchedElement.label)
        put("matched", compactNodeData(matchedElement))
        editableElement?.let { editable ->
            put("editableElementId", editable.elementId)
            put("editableRef", editable.ref)
            put("editableLabel", editable.label)
            put("editableFocused", editable.focused)
            put("editable", compactNodeData(editable))
        }
        put("method", method)
        put("typed", typed)
        val traceTags = buildList {
            add(if (method.contains("activate")) "fill_activation" else "fill_direct")
            if (method.contains("type_focused")) add("focused_input_fallback")
        }
        putTraceTags(*traceTags.toTypedArray())
        expectedText?.let { text ->
            put("textObservedAfter", visibleTextAfter.any { it.contains(text, ignoreCase = true) })
        }
        afterSnapshot?.let { snapshot ->
            putInputDiagnostics(snapshot, matchedElement)
        }
        if (visibleTextAfter.isNotEmpty()) {
            put("visibleTextAfter", buildJsonArray { visibleTextAfter.forEach { add(JsonPrimitive(it)) } })
        }
    }

    private fun JsonObjectBuilder.putDeviceSnapshotData(snapshot: ScreenState) {
        put("snapshotId", snapshot.snapshotId)
        put("foregroundPackage", snapshot.foregroundPackage)
        snapshot.selectedWindowReason?.let { put("selectedWindowReason", it) }
        val selectedWindow = snapshot.windows.firstOrNull { it.selected }
        selectedWindow?.let { window ->
            put(
                "selectedWindow",
                buildJsonObject {
                    put("packageName", window.packageName)
                    put("type", window.type)
                    put("layer", window.layer)
                    put("focused", window.focused)
                    put("active", window.active)
                },
            )
        }
        put("systemUiPresent", snapshot.windows.any { it.packageName == "com.android.systemui" })
        put(
            "keyboardOrInputWindowPresent",
            snapshot.windows.any { window ->
                window.type.contains("input", ignoreCase = true) ||
                    window.type.contains("ime", ignoreCase = true) ||
                    window.className.orEmpty().contains("input", ignoreCase = true)
            },
        )
        snapshot.findFocusedElement()?.let { focused ->
            put("focusedElement", compactNodeData(focused))
        }
    }

    private fun JsonObjectBuilder.putInputDiagnostics(snapshot: ScreenState, matchedElement: ScreenNode?) {
        putDeviceSnapshotData(snapshot)
        matchedElement?.let { put("activationTarget", compactNodeData(it)) }
        put(
            "editableCandidates",
            buildJsonArray {
                snapshot.visibleNodesForDiagnostics()
                    .filter { it.editable }
                    .take(10)
                    .forEach { add(compactNodeData(it)) }
            },
        )
    }

    private fun JsonObjectBuilder.putTraceTags(vararg tags: String) {
        put("traceTags", buildJsonArray { tags.distinct().forEach { add(JsonPrimitive(it)) } })
    }

    private fun compactNodeData(node: ScreenNode): JsonObject = buildJsonObject {
        put("elementId", node.elementId)
        put("ref", node.ref)
        put("label", node.label)
        node.text?.let { put("text", it) }
        node.contentDescription?.let { put("contentDescription", it) }
        node.resourceId?.let { put("resourceId", it) }
        node.className?.let { put("className", it) }
        put("role", node.role)
        put("bounds", buildJsonArray { node.bounds.forEach { add(JsonPrimitive(it)) } })
        put("visible", node.locatorVisible())
        put("enabled", node.enabled)
        put("clickable", node.clickable)
        put("editable", node.editable)
        put("focused", node.focused)
        put("actionable", node.isLocatorActionable())
    }

    private fun ScreenState.findFocusedElement(): ScreenNode? =
        visibleNodesForDiagnostics().firstOrNull { it.elementId == focusedElementId() }
            ?: visibleNodesForDiagnostics().firstOrNull { it.focused }

    private fun ScreenState.visibleNodesForDiagnostics(): List<ScreenNode> = buildList {
        fun visit(node: ScreenNode) {
            if (node.visibleToUser && node.boundsArea() > 0) add(node)
            node.children.forEach(::visit)
        }
        root?.let(::visit)
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
                category = hostCallCategory(name),
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
                category = hostCallCategory(name),
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

    private fun hostCallCategory(name: String): String = when (name) {
        "observeScreen",
        "diffScreen",
        "inspectScreen",
        "findRawNodes",
        "tapRef",
        "tapPoint",
        "tapBounds",
        "scrollRef",
        "scrollScreen",
        -> "diagnostic"
        else -> "supported"
    }

    private fun recordScreenState(snapshot: ScreenState) {
        sessionCoordinator.setLastKnownApp(snapshot.foregroundPackage)
        logStore.recordScreenState(snapshot)
    }

    private fun recordObservationProjectionPerf(
        name: String,
        snapshot: ScreenState,
        payload: ScreenObservationPayload,
        startedAtNanos: Long,
        projectionProfiler: ProjectionProfiler? = null,
    ) {
        recordPerfEvent(
            name = name,
            scope = PerfTelemetry.SCOPE_PROJECTION,
            durationMs = elapsedMs(startedAtNanos),
            attrs =
            mapOf(
                "snapshotId" to snapshot.snapshotId,
                "foregroundPackage" to snapshot.foregroundPackage,
                "mode" to payload.mode,
                "elementCount" to payload.elements.size.toString(),
                "groupCount" to payload.groups.size.toString(),
                "actionCount" to payload.actions.size.toString(),
                "summaryTextLength" to payload.summaryText.orEmpty().length.toString(),
            ),
            phases = projectionProfiler
                ?.phases()
                .orEmpty()
                .map { phase -> PerfPhaseRecord(name = phase.name, durationMs = phase.durationMs) },
        )
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
        private val SUPPORTED_WAIT_STATE_TYPES = setOf("package", "element", "text")
        private const val POLL_INTERVAL_MS = 250L
        private const val LAUNCH_VERIFICATION_TIMEOUT_MS = 2500L
    }
}

private class WaitValueMatcher private constructor(
    private val rawValue: String,
    private val regex: Regex?,
    private val alternatives: List<String>,
) {
    fun matchesPackage(packageName: String): Boolean = regex?.containsMatchIn(packageName) == true ||
        alternatives.any { packageName.contains(it, ignoreCase = true) } ||
        (regex == null && alternatives.isEmpty() && packageName == rawValue)

    fun matchesElementId(elementId: String): Boolean = regex?.containsMatchIn(elementId) == true ||
        alternatives.any { elementId.contains(it, ignoreCase = true) } ||
        (regex == null && alternatives.isEmpty() && elementId == rawValue)

    fun matchesText(text: String): Boolean = regex?.containsMatchIn(text) == true ||
        alternatives.any { text.contains(it, ignoreCase = true) } ||
        (regex == null && alternatives.isEmpty() && text.contains(rawValue, ignoreCase = true))

    companion object {
        fun from(value: String): WaitValueMatcher {
            val regexLiteral = regexLiteral(value)
            val pattern = regexLiteral?.pattern ?: value
            return WaitValueMatcher(
                rawValue = value,
                regex = regexLiteral?.regex ?: alternationRegex(value),
                alternatives = alternationTerms(pattern),
            )
        }

        private fun regexLiteral(value: String): RegexLiteral? {
            val match = REGEX_LITERAL_MATCHER.matchEntire(value) ?: return null
            val pattern = match.groupValues[1]
            val flags = match.groupValues[2]
            val regex = compileRegex(pattern, flags) ?: return null
            return RegexLiteral(pattern = pattern, regex = regex)
        }

        private fun alternationRegex(value: String): Regex? =
            value.takeIf { "|" in it && !it.startsWith("/") }?.let { compileRegex(it, flags = "i") }

        private fun alternationTerms(pattern: String): List<String> = pattern.split('|')
            .map { it.trim() }
            .filter { term -> term.isNotEmpty() && SIMPLE_ALTERNATION_TERM.matches(term) }

        private fun compileRegex(pattern: String, flags: String): Regex? = runCatching {
            val options =
                buildSet {
                    if ('i' in flags) {
                        add(RegexOption.IGNORE_CASE)
                    }
                }
            Regex(pattern, options)
        }.getOrNull()

        private val REGEX_LITERAL_MATCHER = Regex("^/(.*)/([a-zA-Z]*)$")
        private val SIMPLE_ALTERNATION_TERM = Regex("[\\w .:-]+")
    }

    private data class RegexLiteral(val pattern: String, val regex: Regex)
}

private fun ScreenNode.looksLikeTextInputSurface(): Boolean = editable || role == "input" || isSearchLike()

private fun String.normalizedFillIntentLabel(): String {
    val normalized = lowercase()
        .replace(Regex("\\s+"), " ")
        .trim()
    return if (normalized.startsWith("search \"")) {
        "search"
    } else {
        normalized
    }
}

private fun List<Int>.visuallyOverlaps(other: List<Int>): Boolean {
    if (size < 4 || other.size < 4) return false
    val left = maxOf(this[0], other[0])
    val top = maxOf(this[1], other[1])
    val right = minOf(this[2], other[2])
    val bottom = minOf(this[3], other[3])
    val intersection = (right - left).coerceAtLeast(0) * (bottom - top).coerceAtLeast(0)
    val smallerArea = minOf(boundsArea(), other.boundsArea()).coerceAtLeast(1)
    return intersection.toDouble() / smallerArea >= 0.80
}

private fun List<Int>.boundsArea(): Int {
    if (size < 4) return 0
    return (this[2] - this[0]).coerceAtLeast(0) * (this[3] - this[1]).coerceAtLeast(0)
}
