# Recommended Stack

## Decision

Build the product as a Kotlin-first Android app with:

- Koog as the agent-loop harness
- an app-owned Kotlin runtime shell around Koog
- a single script-execution tool exposed to the model
- AccessibilityService-backed phone control exposed through host APIs

Do not use Codex App Server as the foundation. It is a strong app-integration surface for coding agents, but it carries desktop-oriented assumptions around shell execution, PTYs, command approval flows, and remote developer workflows. This product is about reliable on-device phone control.

## Scope

This stack is optimized for a prototype and demo video, not for distribution.

That changes the priorities:

- reliability matters
- debuggability matters
- demo quality matters
- Play Store posture does not matter
- product-grade auth and safety systems do not need to drive the first architecture

Important consequences:

- assume sideloaded or local development install
- do not over-design around Play review
- do not over-design around broad end-user trust and safety flows
- keep the core loop inspectable and easy to iterate on

## Product Shape

The target product is:

- an Android app
- with a voice-first UI
- that runs the runtime on the phone
- that reads and controls the phone through accessibility APIs
- that calls cloud LLM APIs from the phone

More precisely:

- execution is on-device
- reasoning is cloud-backed
- session state is local
- phone control is local
- the app owns orchestration and persistence

The primary interaction loop should be:

1. User speaks or types an instruction.
2. The app captures the current phone state.
3. The app formats the current session state for the agent loop.
4. The model returns one of:
   - a user-facing message
   - a completion or blocked result
   - one `execute_script` call
5. If the model returns `execute_script`, the app runs the script in the embedded JS layer with explicit host bindings.
6. The app records the result, re-observes the phone, and continues until complete or blocked.

## App Layers

Keep the architecture mentally split into three layers:

1. user interface
2. agent loop
3. tool execution

The stack decision is only about the second layer.

- The UI remains fully app-owned.
- Tool execution remains fully app-owned.
- Koog is adopted only for the agent-loop layer.

## Chosen Stack

### Language and platform

- Kotlin
- Android-only for v1

Reasoning:

- Best fit for Android services, permissions, lifecycle, accessibility APIs, local voice APIs, and system integration
- Keeps the initial system debuggable
- Lets the host runtime stay in the same language as the rest of the app

### UI

- Jetpack Compose

Reasoning:

- Native Android UI path
- Good fit for a voice-first app with a live execution view and session timeline

### Agent loop

- Koog 0.7.x
- app-owned session coordinator around Koog

Use Koog for:

- session-oriented agent execution
- message history handling
- explicit history compression
- chat memory
- tool definition and tool-calling flow

Do not use Koog for:

- Android UI and service lifecycle
- phone-control implementation
- script host implementation
- persistence model ownership

Reasoning:

- It gives a real Kotlin-native harness for the agent loop
- It avoids rebuilding basic session and history machinery from scratch
- It is a better fit than heavier server-oriented harnesses for an on-device prototype
- It still leaves the important Android-specific layers fully under app control

The runtime shell around Koog should include:

- `SessionCoordinator`
- prompt and snapshot formatter
- Koog agent adapter
- script execution gateway
- persistence hooks
- explicit stop and interruption handling
- context budgeting and explicit compaction policy

### Model integration

- Koog provider integration for the chosen cloud model
- structured model step output
- one configured tool in v1: `execute_script`

Reasoning:

- The agent loop should stay simple and explicit
- The model does not need a broad menu of Android actions as individual tools
- The script tool keeps the model-facing contract small while still allowing multi-step behavior inside one execution

For this prototype, there are two acceptable auth paths:

1. a tiny relay backend that holds the API key and forwards requests
2. a local development-only key path used only on your own device for demo work

The relay is cleaner. The local key path is acceptable only because this is a side project with no distribution ambitions.

### Script execution

- Kotlin host runtime
- embedded JS execution layer
- a single configured model tool: `execute_script`

The host runtime should expose a narrow API surface to scripts, such as:

- observe the current phone state
- tap an element
- type into an element
- scroll a container
- press back
- press home
- wait for state changes
- speak to the user

Reasoning:

- This keeps the model-facing tool contract very small
- The host runtime can evolve without changing the model contract every time
- One script can perform multiple capability calls before returning
- The app can validate host API calls locally at execution time

Important design rules:

- The model never sees raw Android accessibility objects
- The script never gets raw Android objects either
- Scripts work against normalized snapshots and explicit host bindings

### Phone control

- Android `AccessibilityService`

Reasoning:

- This is the system API intended for observing the accessibility tree and performing actions
- It is the central control surface for the product

The app should convert raw accessibility state into a normalized `UiSnapshot` and expose only that normalized form to the agent loop and script layer.

### Context management

- Koog chat memory
- Koog history compression
- app-owned compaction trigger policy

Reasoning:

- Koog gives the primitives for persistent chat history and explicit compression
- The app should decide when to compress based on the current session budget and runtime policy
- This keeps compaction behavior predictable and easy to debug

### Voice

For v1:

- Android on-device `SpeechRecognizer` where available
- Android `TextToSpeech`

Reasoning:

- Lower latency to start
- Lower cost
- Better offline tolerance for the front end
- Simpler operational model than full realtime duplex audio

### Background execution

- Foreground service for active runs
- WorkManager only for deferred or retryable background work

Reasoning:

- Interactive agent sessions are user-visible, long-lived work
- Android background execution constraints make foreground service the clearer model

For v1, keep this simple:

- one active foreground service
- one active session
- always-visible stop action in the notification

### Persistence

- Room for durable data
- DataStore for preferences and lightweight settings

Store at least:

- sessions
- turns
- normalized phone-state snapshots
- model steps
- script executions
- script host-call logs
- failure summaries

### Security and auth

- Credential Manager for sign-in if sign-in is needed later
- pragmatic local secret handling for prototype use

For the current prototype:

- keep secrets and logs out of the repo
- prefer a relay backend if convenient
- otherwise tolerate a development-only local key path on the demo device

## What Not To Use As The Foundation

### Codex App Server

Do not use it as the main runtime foundation.

Why:

- Best aligned with coding-agent use cases
- Desktop and shell-oriented assumptions
- More adaptation work than value for phone control

### Google ADK for Java

Do not use it as the main runtime foundation for this app.

Why:

- Strong orchestration surface
- Heavy dependency graph
- More server and cloud shaped than needed for this prototype
- Worse fit than Koog for a Kotlin-first Android side project

### LangChain4j

Do not use it as the main runtime foundation for this app.

Why:

- Too thin for the session and history layer we want
- It would still leave too much harness work app-side
- Koog is the better middle ground for this project

## Recommended Module Map

- `app-ui`
- `app-runtime`
- `app-scripting`
- `app-accessibility`
- `app-voice`
- `app-data`
- `app-llm`

### `app-ui`

Responsibilities:

- Compose screens
- voice capture controls
- execution timeline
- settings and onboarding

### `app-runtime`

Responsibilities:

- session lifecycle
- Koog agent-loop integration
- prompt construction
- model step handling
- retries and stopping conditions
- compaction trigger policy

### `app-scripting`

Responsibilities:

- `execute_script` tool contract
- JS runtime embedding
- host API bindings
- script-result normalization
- local execution validation

### `app-accessibility`

Responsibilities:

- accessibility service
- UI tree normalization
- action execution
- focused-node and visible-window tracking

### `app-voice`

Responsibilities:

- speech recognition
- text-to-speech
- audio session state

### `app-data`

Responsibilities:

- Room entities and DAOs
- DataStore settings
- repositories
- session and replay persistence

### `app-llm`

Responsibilities:

- Koog provider configuration
- API auth plumbing
- retries
- request and response logging

## Constraints To Accept Early

### Distribution posture

Treat this as a sideloaded prototype, not a product plan.

That means:

- no Play-first design compromises
- no requirement to justify the concept against store policy
- architecture decisions should optimize for a reliable demo

### Reliability over generality

The system should optimize for:

- predictable session behavior
- easy replay and debugging
- explicit runtime ownership
- fast iteration on the host API surface

It should not optimize for:

- open-ended autonomy
- multi-agent complexity
- broad tool ecosystems
- production-grade safety systems in v1

### One tool surface, many host calls

The model-facing contract should stay small:

- one configured tool: `execute_script`

Inside that script, the runtime can expose many host capabilities. This is the intended simplification.

## Sources

- Android AccessibilityService: https://developer.android.com/reference/android/accessibilityservice/AccessibilityService
- Jetpack Compose: https://developer.android.com/compose
- Android foreground services: https://developer.android.com/develop/background-work/services/fgs
- WorkManager: https://developer.android.com/reference/androidx/work/WorkManager
- Android SpeechRecognizer: https://developer.android.com/reference/android/speech/SpeechRecognizer
- Android TextToSpeech: https://developer.android.com/reference/android/speech/tts/TextToSpeech
- Android Room: https://developer.android.com/jetpack/androidx/releases/room
- Android DataStore: https://developer.android.com/datastore
- Koog quickstart: https://docs.koog.ai/quickstart/
- Koog chat memory: https://docs.koog.ai/chat-memory/
- Koog history compression: https://docs.koog.ai/history-compression/
- Koog class-based tools: https://docs.koog.ai/class-based-tools/
- Koog annotation-based tools: https://docs.koog.ai/annotation-based-tools/
- OpenAI Codex harness post: https://openai.com/index/unlocking-the-codex-harness/
