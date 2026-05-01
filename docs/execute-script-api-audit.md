# execute_script API Audit

> Historical note: this audit documents the removed `execute_script` era. The current model-facing path is `bash` plus `claune-js`, and the current JavaScript API direction is documented in `docs/claune-js-api-design-audit.md`.

## Purpose

This audit captures the current `execute_script` automation-library contract and the failure modes visible in the latest LangSmith trace. It is intentionally not a redesign proposal.

The working thesis is that `execute_script` can remain the single model-facing phone-control tool. The problem is narrower: the script-facing `claune` host API and tool-result contract do not yet make reliable Android automation the shortest, clearest path.

## Evidence Base

- GitHub issue: [#2 Audit execute_script automation library and response contract failures](https://github.com/DivyanshGolyan/claune-android/issues/2)
- Latest trace inspected: `019ddf1e-22a7-77fb-9c60-0a73ea1cbda2`
- Local trace export: `/tmp/claune-latest-trace.json`
- Core files:
  - `android/app/src/main/java/com/divyanshgolyan/claune/android/llm/tools/DefaultTools.kt`
  - `android/app/src/main/java/com/divyanshgolyan/claune/android/scripting/ClauneHostContract.kt`
  - `android/app/src/main/java/com/divyanshgolyan/claune/android/scripting/ScriptHost.kt`
  - `android/app/src/main/java/com/divyanshgolyan/claune/android/scripting/ScreenPayloadMappers.kt`
  - `android/app/src/main/java/com/divyanshgolyan/claune/android/scripting/ScriptModels.kt`
  - `android/app/src/main/java/com/divyanshgolyan/claune/android/scripting/ElementSelectorEngine.kt`
  - `android/app/src/main/java/com/divyanshgolyan/claune/android/scripting/QuickJsScriptRuntime.kt`

Raw trace payloads are not reproduced here because they are large, noisy, and not needed to understand the API contract failures. This audit treats personal UI context as legitimate task context for a personal agent; the concern is whether each returned token helps the next decision.

## Current Architecture

The model sees one phone-control tool: `execute_script`. The tool accepts a JavaScript snippet, runs it in a synchronous QuickJS runtime, and exposes a global `claune` host object.

At the host level, the library already has useful automation concepts:

- interaction observations
- visible elements
- visible groups
- action affordances
- selector-based taps
- text/ref/action taps
- focused typing
- waits
- bounded raw-node search
- compact and full screen projections

So the current system is not missing all automation-library structure. The gap is that the normal path does not consistently compose these concepts into a compact, recoverable, verification-oriented workflow.

## Current Host Surface

| Method | Role | Return shape | Current contract notes |
|---|---|---|---|
| `observeScreen(options?)` | Observation, query | `ScreenObservation` | Defaults to `interactions`; can also return compact/full diagnostic text. Interaction arrays are not fully contract-capped. |
| `diffScreen(options?)` | Observation, verification-ish | `ScreenObservation` | Uses compact diff against prior/baseline snapshot. |
| `inspectScreen(options)` | Diagnostic query | `ScreenInspection` | Visible elements capped 1..80; actionable elements capped 20. |
| `findRawNodes(options)` | Diagnostic, escape hatch | `RawNodeSearchResult` | Searches visible nodes, not entire raw tree; matches capped 1..50; context labels capped. |
| `listInstalledApps()` | Query | `InstalledApp[]` | Not capped in the contract. |
| `launchApp(packageName)` | Action plus verification | `HostSuccessOutcome` | Verifies foreground package with fixed poll window. |
| `tapRef(ref)` | Action | `HostSuccessOutcome` | Recaptures current snapshot and taps matching actionable ref. |
| `tapText(text, options?, first?)` | Action with selector lookup | `HostSuccessOutcome` | Blocks ambiguity unless `first` is used. |
| `tapSelector(selector)` | Action with selector lookup | `HostSuccessOutcome` | Selector must have criteria; blocks ambiguous top-ranked matches. |
| `tapElement(elementId)` | Low-level action | `HostSuccessOutcome` | Direct element id tap; weaker diagnostics. |
| `performAction(actionId)` | Action affordance | `HostSuccessOutcome` | Action affordances include click/type/scroll kinds, but this only performs click. |
| `tapBounds(bounds)` | Coordinate escape hatch | `HostSuccessOutcome` | Taps center of inspected bounds; provenance is documentation-governed. |
| `tapPoint(x, y)` | Coordinate escape hatch | `HostSuccessOutcome` | Lowest-level tap; provenance is documentation-governed. |
| `scrollRef(ref, direction)` | Action | `HostSuccessOutcome` | Contract exposes up/down while implementation also accepts left/right. |
| `scrollScreen(direction)` | Action | `HostSuccessOutcome` | Chooses best visible scrollable element. Direction contract drift also applies. |
| `scrollContainer(elementId, direction)` | Low-level action | `HostSuccessOutcome` | Direct element id scroll. |
| `focusSelector(selector, timeoutMs)` | Action plus wait | `HostSuccessOutcome` | Activates selector target and waits for editable target. Timeout is caller-controlled. |
| `typeIntoSelector(selector, text)` | Action plus wait | `HostSuccessOutcome` | Focuses selector with default wait, then types into focused editable. |
| `typeIntoFocused(text)` | Action | `HostSuccessOutcome` | Requires focused editable element. |
| `typeIntoElement(elementId, text)` | Low-level action | `HostSuccessOutcome` | Direct element id typing. |
| `waitForSelector(selector, timeoutMs)` | Wait, verification-ish | `HostSuccessOutcome` | Waits for selector match; timeout is caller-controlled. |
| `waitForState(type, value, timeoutMs)` | Wait, verification-ish | `HostSuccessOutcome` | Supports package, element id, and visible text. |
| `pressBack()` | Navigation action | `HostSuccessOutcome` | No built-in postcondition. |
| `pressHome()` | Navigation action | `HostSuccessOutcome` | No built-in postcondition. |
| `findElement(screen, selector)` | Pure JS helper | `VisibleElement | null` | Searches supplied `screen.elements`. |
| `findGroup(screen, selector)` | Pure JS helper | `VisibleGroup | null` | Searches supplied `screen.groups`. |
| `findAction(screenOrGroup, selector)` | Pure JS helper | `ActionAffordance | null` | Can use the last observed screen for group action ids. |

## Tool Response Contract

`ExecuteScriptToolDefinition.execute()` currently builds the model-visible result as:

```json
{
  "ok": true,
  "summary": "Script completed with N host calls.",
  "error": null,
  "scriptData": "... arbitrary script return JSON ...",
  "postActionObservation": "... compact/diff screen payload ..."
}
```

The same payload is serialized twice:

- `content`: single `TextContent(encodedJson)`, which is model-visible and sent to the model provider.
- `details`: structured JSON, used for local artifacts and debug telemetry.

The key consequence is that any large `scriptData` becomes model-visible immediately, then also appears in local/debug trace structures. That can be appropriate when the data is decision-relevant; the problem is that the boundary does not distinguish high-signal evidence from low-signal diagnostic bulk.

## Observation Contract

There are three observation families:

- `observeScreen({ mode: "interactions" })`, the default, returns structured `windows`, `selectedWindow`, `summaryText`, `elements`, `groups`, `actions`, and count diagnostics.
- `observeScreen({ mode: "compact" | "full" })` returns canonical text instead of interaction arrays.
- `postActionObservation` is generated outside the script after every `execute_script` call using `buildScreenObservation(...).toPayload()`, so it carries compact snapshot/diff metadata and text, not rich `elements/groups/actions`.

Important mismatch: inside a script, the model can return the full rich observation as `scriptData`; outside the script, the automatic post-action observation is compact/diff only. In the inspected trace, the model leaned heavily on `scriptData` and raw-node counts rather than on the automatic post-action observation.

## Boundedness

Existing bounds:

- compact canonical text: capped in screen projection
- full canonical text: capped in screen projection
- diffs: capped in screen projection
- `inspectScreen.visibleElements`: coerced to 1..80
- `inspectScreen.actionableElements`: capped at 20
- `findRawNodes.matches`: coerced to 1..50
- `findRawNodes.ancestorLabels`: capped
- `findRawNodes.childLabels`: capped
- interaction summary text: line-limited
- groups: capped at 80

Weak or missing bounds:

- `execute_script.scriptData`: no model-visible size cap
- `observeScreen({ mode: "interactions" }).elements`: not clearly capped in the public contract
- `observeScreen({ mode: "interactions" }).actions`: not clearly capped in the public contract
- `listInstalledApps()`: not capped in the public contract
- local/debug telemetry duplicates large payloads through both `content` and `details`
- host-call result artifacts can include large data-call JSON

## Failure And Recovery Contract

The runtime distinguishes data calls from action calls in an important way.

Data/observation methods generally return payloads:

- `observeScreen`
- `diffScreen`
- `inspectScreen`
- `findRawNodes`
- `listInstalledApps`

Action-like methods return `HostSuccessOutcome` natively, but the generated JavaScript bootstrap wraps them in `__clauneRequireOutcome`. If `ok !== true`, the action throws a JavaScript error instead of returning a structured `{ ok: false }` value to the script.

That makes the common happy path concise, but it weakens programmatic recovery:

- scripts must use `try/catch` to inspect action failures
- query failures and action failures have different shapes
- error classes are mostly prose in `message`
- retryability and recoverability are not first-class fields

## Verification Contract

Current verification support:

- `launchApp` verifies foreground package.
- `waitForState` can wait for package, element id, or visible text.
- `waitForSelector` can wait for selector match.
- `finish_run` requires evidence for completed status.

Missing or weak verification concepts:

- no first-class assertion result type
- no distinction between action success and task success in `HostSuccessOutcome`
- no explicit absence assertion
- no count assertion
- no scoped assertion
- no stable-for-duration assertion
- no post-action condition attached to an action
- no structured uncertainty state

In the inspected trace, every `execute_script` returned `ok=true`, but that only proved that each script executed. Completion evidence still relied mostly on absence of raw-node matches, not a direct task-level success predicate.

## Trace Findings

Trace `019ddf1e-22a7-77fb-9c60-0a73ea1cbda2` showed:

- 9 `execute_script` calls
- 15 `observeScreen` calls
- 15 `findRawNodes` calls across 7 of 9 scripts
- 4 `tapRef` calls
- largest tool result around 78k result bytes
- total `execute_script` result bytes around 217k
- model-visible rendered tool content around 112k

The repeated pattern was:

1. observe current screen
2. raw-search for target text
3. select nearest actionable node manually
4. tap a ref
5. observe or raw-search again
6. count remaining raw matches

This suggests that the model could write JavaScript, but the host library did not provide a sufficiently strong normal path for this workflow.

## Design Gaps

### 1. No First-Class Locator Contract

There are selectors, refs, text taps, action ids, element ids, bounds, points, and pure JS helper searches. There is not yet one coherent locator abstraction with:

- scoping
- strictness
- retry behavior
- actionability
- stale-state rules
- diagnostic expansion
- consistent result shape

This causes scripts to compose low-level search and action logic manually.

### 2. Observation And Diagnostics Are Too Easy To Return Wholesale

The host has bounded search helpers, but the script return channel can still forward any returned object. If the script returns a screen observation, raw search results, or multiple diagnostic payloads, the tool includes all of it in `scriptData`.

The public contract asks the model to return compact data, but compactness is not enforced at the boundary.

### 3. The Automatic Post-Action Observation Is Helpful But Not Sufficient

`postActionObservation` gives a current compact/diff view after the script. That is useful.

However, because it does not include rich interaction arrays, it may not be enough for the next decision. The model then returns its own richer observation in `scriptData`, duplicating state and increasing context size.

### 4. Action Failures Are Not Uniform Structured Values

Throwing on failed `HostSuccessOutcome` creates a concise happy path, but makes recoverability depend on exception text and `try/catch`.

An automation library needs errors that are classified and inspectable:

- no match
- ambiguous match
- not visible
- not actionable
- stale ref
- covered
- timeout
- unsafe action
- invalid selector

Some of these concepts already exist implicitly in messages; they are not yet stable fields.

### 5. Verification Is Too Thin

The host can wait for simple conditions, but cannot yet express task-level evidence well. This encourages scripts to infer success from incidental UI state, such as absence of a control.

The false-success risk is not that the model cannot click. It is that the API does not strongly separate:

- script executed
- action performed
- UI changed
- expected state observed
- user goal complete

### 6. Escape Hatches Are Documentation-Governed

`tapBounds` and `tapPoint` are documented as fallback/debug paths, but the runtime does not require provenance such as:

- which snapshot produced the bounds
- whether the target was visible
- whether semantic/ref/action taps were attempted first
- whether the tap was followed by verification

This makes misuse easy when the model is under pressure.

### 7. Contract Drift Exists

Examples found:

- TypeScript scroll directions expose `up | down`, while implementation accepts `left/right`.
- Action affordances expose `kind: "click" | "type" | "scroll"`, while `performAction` only performs click actions.

These are small individually, but they reduce trust in the generated contract.

### 8. Signal Is Not A First-Class Contract

User-visible data can appear in many overlapping places:

- `label`
- `text`
- `contentDescription`
- `visibleText`
- `summaryText`
- `canonicalText`
- `matchedText`
- `ancestorLabels`
- `childLabels`
- `resourceId`
- `elementId`
- bounds and centers

That is not inherently wrong for a personal agent. The model may need personal UI details to act correctly. The problem is that the current contract does not label or separate decision-relevant evidence from repeated diagnostic representations of the same state.

## Existing Test Coverage

Relevant coverage exists:

- prompt/tool contract tests in `PiAgentModelGatewayTest`
- scripting runtime tests in `QuickJsScriptRuntimeTest`
- host API behavior tests in `ScriptHostTest`
- artifact serialization tests in `AgentRunArtifactStoreTest`
- debug telemetry tests in `ClauneTelemetryTest`
- memory reflection and question coordinator tests

Known gaps:

- no audit matrix mapping contract clauses to tests
- no test asserting maximum model-visible `execute_script` result size
- no test detecting duplicate script-returned observations plus `postActionObservation`
- no golden replay harness for persisted `script-executions.json`, `host-calls.json`, and `agent-events.json`
- no unit test found for `ToolBudget` blocking `execute_script` during memory reflection
- no trace-fixture test against a real exported trace

## Evaluation Axes For Follow-Up Work

Use these axes to judge future changes:

| Axis | Question |
|---|---|
| Abstraction altitude | Does the API remove repeated low-level scripting without hardcoding app-specific flows? |
| Observation contract | Is the default observation sufficient, bounded, and not redundant? |
| Query contract | Does search expose ambiguity, scope, ranking, and truncation clearly? |
| Action contract | Are actionability, stale refs, unsafe actions, and effects represented explicitly? |
| Verification contract | Can scripts prove task-level success without over-trusting action success? |
| Return-value contract | Are model-visible results compact, stable, and evidence-preserving? |
| Error semantics | Can scripts recover from structured error classes instead of parsing prose? |
| Signal quality | Does the default path return decision-relevant evidence rather than repeated low-signal diagnostics? |
| Telemetry | Can large diagnostic payloads remain inspectable without entering model context twice? |

## Candidate Problem Clusters

These are clusters for follow-up planning, not proposed solutions.

1. **Model-visible result shaping**
   - cap or summarize `scriptData`
   - avoid duplicating observations
   - separate model-visible evidence from debug details
   - expose size/truncation metadata

2. **Observation contract**
   - decide what the default post-action observation should contain
   - make interaction arrays bounded by contract
   - make raw/diagnostic expansion explicit

3. **Verification contract**
   - add or define assertion semantics
   - distinguish action success from task success
   - represent uncertainty and absence carefully

4. **Locator/action contract**
   - unify selector/ref/action/bounds behavior
   - make stale state and ambiguity explicit
   - close contract drift around action kinds and directions

5. **Debug and telemetry contract**
   - identify high-volume diagnostic fields
   - reduce duplicated trace payloads
   - keep full diagnostics available outside model-visible context

## Current Read

The highest-confidence first slice is the model-visible result and observation contract. It has the clearest trace evidence, the largest context impact, and does not require deciding the final locator or verification API.
