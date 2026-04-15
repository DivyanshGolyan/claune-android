# V1 Architecture

## Goal

Define a first version that is small enough to build and debug, but shaped correctly for a phone-native agent.

V1 should be:

- on-device in execution
- voice-first but not voice-only
- tool-driven
- supervised for risky actions
- replayable and inspectable

V1 should not be:

- a general-purpose autonomous assistant
- a multi-agent system
- a background automation platform

This document is intentionally prototype-first. It is written for a demoable side project, not for a distribution-ready product.

## System Overview

The system has three major layers:

1. Android app and service layer
2. agent runtime layer
3. phone control layer

## High-Level Architecture

```text
User
  -> Compose UI
  -> SessionCoordinator
  -> AgentLoop
  -> LLM Client
  -> Tool Executor
  -> Accessibility Service
  -> Android UI State

Persisted alongside the loop:
  -> Room database
  -> DataStore settings
```

## Core Runtime Contract

The runtime should process one active session at a time.

The key runtime objects are:

- `Session`
- `Turn`
- `UiSnapshot`
- `ToolCall`
- `ToolResult`
- `ApprovalRequest`
- `RunLogEvent`

The runtime should execute at most one model-selected tool call per turn.

## State Model

### Session

Represents one user goal.

Suggested fields:

- `sessionId`
- `createdAt`
- `status`
- `goal`
- `lastKnownApp`
- `lastSnapshotId`
- `requiresUserAttention`

### Turn

Represents one cycle of observe -> think -> act.

Suggested fields:

- `turnId`
- `sessionId`
- `index`
- `userInput`
- `snapshotId`
- `modelRequest`
- `modelResponse`
- `toolCallId`
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

### ToolCall

Suggested fields:

- `toolCallId`
- `sessionId`
- `turnId`
- `toolName`
- `argumentsJson`
- `status`
- `startedAt`
- `finishedAt`

### ApprovalRequest

Suggested fields:

- `approvalId`
- `sessionId`
- `turnId`
- `reason`
- `riskLevel`
- `presentedAction`
- `decision`
- `decidedAt`

## Service Boundary

### Compose app

Owns:

- onboarding
- sign-in
- voice and text input
- session display
- approval prompts
- execution timeline

### Foreground service

Owns:

- active session runtime
- network calls to the LLM
- tool execution coordination
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

### `ModelGateway`

```kotlin
interface ModelGateway {
    suspend fun nextStep(input: ModelTurnInput): ModelTurnOutput
}
```

### `ApprovalGateway`

```kotlin
interface ApprovalGateway {
    suspend fun requestApproval(request: ApprovalRequestData): ApprovalDecision
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
- data that the model cannot use

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

## Tool API

Keep v1 tools small and explicit.

### Read tools

- `observe_phone`

### Action tools

- `tap_element`
- `type_into_element`
- `scroll_container`
- `press_back`
- `press_home`
- `wait_for_state`

### User interaction tools

- `speak_to_user`

Approvals are runtime policy, not model-selected tools.

## V1 Tool Definitions

### `observe_phone`

Purpose:

- Capture a fresh `UiSnapshot`

Returns:

- normalized screen state

### `tap_element`

Arguments:

- `elementId`

Rules:

- only valid on currently visible and actionable elements

### `type_into_element`

Arguments:

- `elementId`
- `text`

Rules:

- only valid on editable targets

### `scroll_container`

Arguments:

- `elementId`
- `direction`

### `wait_for_state`

Arguments:

- `condition`
- `timeoutMs`

Purpose:

- absorb UI latency without forcing the model to guess timing

Allowed `condition` values in v1:

- `package_changed`
- `element_appeared`
- `element_disappeared`
- `text_visible`
- `text_hidden`

## Agent Loop

The runtime loop should be intentionally simple.

```text
1. Capture latest snapshot
2. Build model input from:
   - user goal
   - current snapshot
   - recent action history
   - pending constraints
3. Ask model for next step
4. Validate output
5. If approval required, prompt user
6. Execute exactly one tool call
7. Persist events
8. Re-observe and continue
9. Stop on success, user cancellation, or unrecoverable failure
```

## Model Output Constraints

The model should not be allowed to emit arbitrary text and arbitrary tools in the same step.

Use a strict output shape such as:

- `message_to_user`
- `tool_call`
- `completion`
- `blocked`

Reasons:

- simpler UI rendering
- simpler persistence
- easier retry behavior
- easier debugging

The model should never choose whether an action needs approval. That is a runtime decision.

## Validation Layer

Every model-produced tool call must pass local validation before execution.

Checks should include:

- required arguments present
- referenced element exists in latest snapshot
- action allowed for the element type
- action allowed under current permission state
- action not blocked by policy

If validation fails:

- do not execute
- return a structured failure to the runtime
- allow the model one recovery attempt before escalating to the user

## Safety And Approvals

All risky actions should go through a policy layer.

### Low risk

Examples:

- scroll
- navigate back
- open a settings subpage

Policy:

- auto-execute

### Medium risk

Examples:

- typing into a form
- opening an external app

Policy:

- execute if within active user session and not policy-blocked

### High risk

Examples:

- final submit
- payment
- delete
- grant system permission

Policy:

- always require explicit approval

## Failure Handling

V1 should expect frequent UI ambiguity and recover predictably.

Failure classes:

- target missing
- stale snapshot
- action rejected by system
- app changed unexpectedly
- model chose wrong next step
- network failure
- session interrupted

Recovery strategy:

1. re-observe
2. retry once with fresh state
3. ask user if ambiguity remains

## Logging And Replay

Every session should be replayable after the fact.

Persist:

- user inputs
- snapshots
- model requests and responses
- tool calls
- approvals
- action results
- terminal session outcome

This is essential for debugging and evaluation.

For the prototype:

- screenshots should default to off
- structured logs should default to on
- screenshots can be enabled only for demo recording and debugging

## V1 User Experience

The initial app should have five screens:

1. Onboarding and permission setup
2. Main assistant screen
3. Live execution screen
4. Approval prompt sheet
5. History and replay screen

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
    app-tools/
    app-accessibility/
    app-voice/
    app-data/
    app-security/
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

### Milestone 3: Manual tool execution

Build:

- tap
- type
- scroll
- back
- wait

Exit criteria:

- app can execute tools reliably from a debug UI without the model

### Milestone 4: Single-step model loop

Build:

- OpenAI client integration
- one-turn prompt builder
- tool-call validation
- single-step execution

Exit criteria:

- model can inspect a snapshot and choose one valid next action

### Milestone 5: Full supervised session

Build:

- repeated loop
- approval prompts
- voice input
- TTS output
- history and replay

Exit criteria:

- user can complete narrow tasks with supervision

## Open Questions To Resolve Early

- relay backend vs development-only local key
- minimum Android API level
- whether screenshots are stored for replay by default
- exact consent model for action logging and analytics
