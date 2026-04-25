# V1 Architecture

## Goal

Define a first version that is small enough to build and debug, but shaped correctly for a phone-native agent.

V1 should be:

- on-device in execution
- voice-first but not voice-only
- replayable and inspectable
- centered on one active user session
- powered by the vendored `pi-agent-kotlin` agent loop
- driven through one model-facing tool: `execute_script`

V1 should not be:

- a general-purpose autonomous assistant
- a multi-agent system
- a background automation platform
- a production-grade safety architecture

This document is intentionally prototype-first. It is written for a demoable side project, not for a distribution-ready product.

## System Overview

The system has three major layers:

1. Android app and service layer
2. agent loop layer
3. tool execution layer

The first and third layers are app-owned.

The second layer uses `pi-agent-kotlin`.

## High-Level Architecture

```text
User
  -> Compose UI
  -> SessionCoordinator
  -> AgentLoop
  -> execute_script
  -> JS Runtime
  -> Host Bindings
  -> Accessibility Service
  -> Android UI State

Persisted alongside the loop:
  -> Room database
  -> DataStore settings
```

## Core Runtime Contract

The runtime keeps one active session and processes one active run at a time.

The key runtime objects are:

- `Session`
- `Run`
- `ModelStep`
- `UiSnapshot`
- `ScriptExecution`
- `HostCallEvent`
- `RunLogEvent`

The model-facing tool surface is intentionally tiny:

- phone control: `execute_script`
- run outcome: `finish_run`
- user decision: `ask_user`

Each run may:

- call `execute_script` zero or more times
- call `ask_user` only when user input is required before continuing
- call `finish_run` exactly once with status `completed` or `blocked`

The script itself may perform multiple host API calls before returning.

## State Model

### Session

Represents the durable conversation/thread. A session can contain multiple user messages and multiple runs over time.

Suggested fields:

- `sessionId`
- `createdAt`
- `status`
- `title`
- `lastKnownApp`
- `lastSnapshotId`
- `lastRunId`
- `requiresUserAttention`

### Run

Represents the agent processing one user message/request on the phone.

Suggested fields:

- `runId`
- `sessionId`
- `userMessage`
- `snapshotId`
- `modelStepId`
- `scriptExecutionId`
- `resultSummary`
- `status`

### UiSnapshot

Represents the normalized visible phone state.

Suggested fields:

- `snapshotId`
- `capturedAt`
- `foregroundPackage`
- `windows`
- `visibleText`
- `focusedElement`
- `scrollContainers`
- `actionableElements`
- `screenshotPath`

### ModelStep

Represents the structured output returned by the agent loop for a single inference step.

Suggested fields:

- `modelStepId`
- `sessionId`
- `runId`
- `kind`
- `messageText`
- `scriptSource`
- `structuredPayloadJson`
- `status`
- `startedAt`
- `finishedAt`

### ScriptExecution

Represents one `execute_script` run.

Suggested fields:

- `scriptExecutionId`
- `runId`
- `language`
- `source`
- `status`
- `resultJson`
- `startedAt`
- `finishedAt`

### HostCallEvent

Represents one host API call made by a running script.

Suggested fields:

- `hostCallId`
- `scriptExecutionId`
- `runId`
- `name`
- `argumentsJson`
- `resultJson`
- `startedAt`
- `finishedAt`

## Service Boundary

### Compose app

Owns:

- onboarding
- voice and text input
- session display
- run display
- execution timeline
- replay view

### Foreground service

Owns:

- active run runtime
- `pi-agent-kotlin` agent execution
- script execution coordination
- durable session progress

### Accessibility service

Owns:

- observing UI state
- producing normalized snapshots
- performing UI actions

Important rule:

- The foreground service should not manipulate raw accessibility nodes directly.
- It should depend on a narrow interface exposed by the accessibility layer.

For the prototype, keep the service model simple:

- one foreground service
- one active session
- one active run
- one explicit stop action in the persistent notification

## Internal Interfaces

Use explicit interfaces from the start.

### `PhoneObserver`

```kotlin
interface PhoneObserver {
    suspend fun captureSnapshot(): UiSnapshot
}
```

### `PhoneActuator`

```kotlin
interface PhoneActuator {
    suspend fun tap(target: ElementRef): ActionResult
    suspend fun type(target: ElementRef, text: String): ActionResult
    suspend fun scroll(target: ElementRef, direction: ScrollDirection): ActionResult
    suspend fun pressBack(): ActionResult
    suspend fun pressHome(): ActionResult
}
```

### `AgentLoop`

```kotlin
interface AgentLoop {
    suspend fun nextStep(input: AgentTurnInput): AgentTurnOutput
}
```

### `ScriptRuntime`

```kotlin
interface ScriptRuntime {
    suspend fun execute(request: ScriptExecutionRequest): ScriptExecutionResult
}
```

## Normalized Snapshot Shape

The normalized snapshot is one of the most important decisions in the system.

It should include:

- stable synthetic element ids
- stable element signatures used for re-identification
- role or type
- text and content description
- bounds
- enabled and clickable flags
- editable flag
- selected and checked flags
- hierarchical hints when useful
- visibility ranking

It should not include:

- raw Android objects
- every low-level property
- data that the model or script cannot use

### Element identity strategy

`elementId` should not be a random identifier. It should be derived from a stable signature built from the strongest available fields such as:

- package name
- class name
- visible text
- content description
- view resource id if present
- clickable or editable role
- approximate bounds
- nearby parent or sibling hints when needed

Re-identification rules:

1. Try exact signature match in the latest snapshot.
2. If exact match fails, try a scored fuzzy match over the same local region.
3. If confidence is too low, fail as stale instead of guessing.

Stale reference behavior:

- do not silently retarget to a weak match
- return a structured stale-element failure
- re-observe and let the model recover on the next turn

Example:

```json
{
  "foregroundPackage": "com.android.settings",
  "actionableElements": [
    {
      "id": "el_12",
      "role": "button",
      "label": "Wi-Fi",
      "hint": "Double tap to activate",
      "clickable": true,
      "editable": false,
      "bounds": [44, 380, 1036, 512]
    }
  ]
}
```

## Model Tool Contract

The model should not use final free-form assistant text as the run outcome.

Use the explicit tool surface:

- `execute_script`
- `ask_user`
- `finish_run`

Reasons:

- simpler UI rendering
- simpler persistence
- easier retry behavior
- easier debugging

`execute_script` is the only phone-control tool in v1. `finish_run` is the only terminal outcome tool. `ask_user` is only for a user decision needed before the same run can continue.

The script body is the unit of action. The script can call multiple host APIs before returning.

## Script Tool Contract

### `execute_script`

Purpose:

- run one model-authored JS script against the current host API surface

Suggested arguments:

- `script`
- `summary`

Suggested return shape:

- `ok`
- `summary`
- `data`
- `hostCalls`
- `error`

Important runtime rules:

- scripts execute against explicit host bindings only
- scripts do not receive raw Android objects
- scripts do not mutate app state except through host APIs
- every host API call is logged

## Host API Surface

Keep the v1 host API small and explicit.

Initial host capabilities:

- `observePhone()`
- `tapElement(elementId)`
- `typeIntoElement(elementId, text)`
- `scrollContainer(elementId, direction)`
- `pressBack()`
- `pressHome()`
- `waitForState(condition, timeoutMs)`
- `speakToUser(text)`

These are not model-facing tools. They are script-facing host bindings.

## Agent Loop

The runtime loop should be intentionally simple.

```text
1. Capture latest snapshot
2. Build model input from:
   - current user message/request
   - current snapshot
   - recent session history
   - recent script results
   - pending runtime constraints
3. Ask the `pi-agent-kotlin` session for the next step
4. If the model calls `execute_script`, run the script in the JS runtime
5. If the model calls `ask_user`, render the question and continue after the answer
6. If the model calls `finish_run`, record the completed or blocked outcome
7. Persist model step, script execution, and host-call events
8. Re-observe and continue
9. Stop on `finish_run`, user cancellation, or unrecoverable failure
```

## Agent Runtime Integration

`pi-agent-kotlin` is used for:

- managing the agent loop
- handling session-scoped history
- explicit history compression
- structured tool-calling flow

The app still owns:

- Android services
- session persistence model
- script runtime
- host bindings
- interruption semantics

For v1, the agent runtime integration should stay simple:

- one active agent session
- one active foreground runtime
- explicit compaction trigger policy owned by the app

## Context Management

Use `pi-agent-kotlin` chat memory and history compression, but keep the trigger policy app-owned.

The app should decide when to compact based on:

- current prompt size
- message count
- script execution history size
- model limits for the chosen provider

Practical v1 rule:

- compact only at turn boundaries
- compact before the next inference step when the current session budget is too large

## Validation Layer

Every model-produced `execute_script` step must pass local validation before execution.

Checks should include:

- required fields present
- script payload is non-empty
- script size is within a local limit
- runtime is in an executable state
- required services are connected

Every host API call must also pass local validation at execution time.

Checks should include:

- referenced element exists in the latest snapshot
- action is allowed for the element type
- action is supported in the current device state

If validation fails:

- do not execute the invalid action
- return a structured failure to the running script or session
- let the next model step recover with fresh state

## Safety Posture

This prototype does not need a full approval system yet.

For v1:

- do not add heavy approval plumbing
- do block impossible or invalid host calls locally
- do surface explicit errors instead of guessing

Approval workflows can be added later if the demo requires them.

## Failure Handling

V1 should expect frequent UI ambiguity and recover predictably.

Failure classes:

- target missing
- stale snapshot
- host action rejected by system
- app changed unexpectedly
- model chose a bad script
- script runtime error
- network failure
- session interrupted

Recovery strategy:

1. re-observe
2. retry once with fresh state if safe
3. continue the loop with the structured failure
4. stop and surface the issue if recovery stalls

## Logging And Replay

Every session should be replayable after the fact.

Persist:

- user inputs
- snapshots
- model steps
- script sources
- script results
- host API calls
- terminal session outcome

This is essential for debugging and evaluation.

For the prototype:

- screenshots should default to off
- structured logs should default to on
- screenshots can be enabled only for demo recording and debugging

## V1 User Experience

The initial app should have four screens:

1. Onboarding and permission setup
2. Main assistant screen
3. Live execution screen
4. History and replay screen

## Suggested Repo Layout

```text
claune-android/
  README.md
  docs/
    recommended-stack.md
    v1-architecture.md
  android/
    app/
    app-ui/
    app-runtime/
    app-scripting/
    app-accessibility/
    app-voice/
    app-data/
    app-llm/
```

## First Five Milestones

### Milestone 0: Prototype posture

Resolve:

- sideload-only distribution assumption
- auth path: tiny relay backend or development-only local key
- minimum Android version for the prototype
- whether screenshots are enabled for debugging

Exit criteria:

- prototype trust boundary is explicit
- no product-only concerns are blocking implementation

### Milestone 1: Skeleton Android app

Build:

- Compose app shell
- foreground service skeleton
- accessibility service skeleton
- Room and DataStore setup

Exit criteria:

- app launches
- permissions and service wiring exist
- session state can be stored locally

### Milestone 2: Snapshot pipeline

Build:

- normalized `UiSnapshot`
- snapshot viewer in debug UI
- basic screenshot linkage

Exit criteria:

- can inspect current screen as structured state

### Milestone 3: Host API and script runtime

Build:

- JS runtime embedding
- host API bindings for observe, tap, type, scroll, back, and wait
- debug UI for manual script execution

Exit criteria:

- app can execute scripts reliably from a debug UI without the model

### Milestone 4: Single-step agent loop

Build:

- `pi-agent-kotlin` integration
- one-turn prompt builder
- `execute_script`, `ask_user`, and `finish_run` wiring

Exit criteria:

- model can inspect a snapshot and use the expected tool for action, user decision, or final outcome

### Milestone 5: Full session loop

Build:

- repeated loop
- voice input
- TTS output
- history and replay
- explicit compaction trigger policy

Exit criteria:

- user can complete narrow tasks with a replayable session

## Open Questions To Resolve Early

- relay backend vs development-only local key
- minimum Android API level
- which embedded JS engine to use
- whether screenshots are stored for replay by default
