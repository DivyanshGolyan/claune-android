# Recommended Stack

## Decision

Build the product as a Kotlin-first Android app with a thin custom agent runtime.

Do not use Codex App Server as the foundation. It is a strong app-integration surface for a coding agent, but it carries desktop-oriented assumptions around shell execution, PTYs, command approval flows, and the broader coding harness. For this product, the main problem is reliable phone control on-device, not remote code execution.

## Scope

This stack is optimized for a prototype and demo video, not for distribution.

That changes the priorities:

- reliability matters
- debuggability matters
- demo quality matters
- Play Store posture does not matter
- product-grade auth and retention policies do not need to drive the architecture

Important consequences:

- assume sideloaded or local development install
- do not over-design around Play review
- do not over-design around mass-user account security
- still avoid obviously bad habits if they make the prototype harder to evolve later

## Product Shape

The target product is:

- an Android app
- with a voice-first UI
- that runs the agent on the phone
- that reads and controls the phone through accessibility APIs
- that calls cloud LLM APIs directly from the phone

More precisely:

- execution is on-device
- reasoning is cloud-backed
- the runtime is local
- the control loop is supervised

The primary interaction loop should be:

1. User speaks or types an instruction.
2. The app captures the current phone state.
3. The model chooses the next tool call or asks a clarifying question.
4. The app executes the tool.
5. The app re-observes the phone state.
6. The loop repeats until the goal is complete or blocked.

## Chosen Stack

### Language and platform

- Kotlin
- Android-only for v1

Reasoning:

- Best fit for Android services, permissions, lifecycle, accessibility APIs, local voice APIs, and system integration
- Avoids a cross-language bridge on day one
- Keeps the initial system debuggable and operationally simple

### UI

- Jetpack Compose

Reasoning:

- Native Android UI path
- Good fit for a voice-first app with a live execution view and approval prompts

### Agent runtime

- Custom Kotlin orchestrator

Do not start with a generic agent framework.

Reasoning:

- The tool surface is small and domain-specific
- The runtime needs explicit control over permissions, retries, confirmations, and lifecycle
- A small orchestrator will be easier to reason about than adapting a server-oriented framework

The runtime should include:

- prompt builder
- tool registry
- model client
- tool executor
- state reducer
- approval gate
- persistence hooks

### Model integration

- Official OpenAI Java SDK from Kotlin
- Responses-style tool calling with typed schemas

Reasoning:

- Strongest fit for a custom, tool-driven loop
- Keeps the integration explicit
- Avoids importing a larger harness only to unwrap it again

For this prototype, there are two acceptable auth paths:

1. a tiny relay backend that holds the OpenAI API key and forwards requests
2. a local development-only key path used only on your own device for demo work

The relay is cleaner. The local key path is acceptable only because this is a side project with no distribution ambitions.

### Phone control

- Android `AccessibilityService`

Reasoning:

- This is the system API intended for observing the accessibility tree and performing actions
- It is the central control surface for the product

Important design rule:

- The LLM should never see raw Android node objects
- The app should convert them into a normalized `UiSnapshot`

### Voice

For v1:

- Android on-device `SpeechRecognizer` where available
- Android `TextToSpeech`

Reasoning:

- Lower latency to start
- Lower cost
- Better offline tolerance for the front end
- Simpler operational model than full realtime duplex audio

For v2:

- Add realtime voice only if the product actually needs conversational interruption, low-latency streaming, or natural spoken back-and-forth

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

- conversations
- tool calls
- action results
- approvals
- normalized phone-state snapshots
- failure summaries

### Security and auth

- Credential Manager for sign-in
- BiometricPrompt for local confirmation of risky actions or unlocking saved credentials

If the product has its own backend, prefer backend-mediated auth and token issuance over storing raw provider credentials directly on the phone.

## What Not To Use As The Foundation

### Codex App Server

Do not use it as the main runtime foundation.

Why:

- Best aligned with coding-agent use cases
- Heavy dependency graph
- Desktop and shell-oriented assumptions
- More adaptation work than value for phone control

### Generic agent frameworks

Examples considered:

- Google ADK for Java
- Koog
- LangChain4j

These may become useful later, but they are not the best v1 foundation.

Why:

- They add abstraction before the core control loop is stable
- This product needs predictable state transitions and strong runtime control more than framework flexibility

For a demo build, this matters even more. Every extra framework is another failure surface when recording.

## Recommended Module Map

- `app-ui`
- `app-runtime`
- `app-tools`
- `app-accessibility`
- `app-voice`
- `app-data`
- `app-security`
- `app-llm`

### `app-ui`

Responsibilities:

- Compose screens
- voice capture controls
- execution timeline
- approvals and confirmations
- settings and onboarding

### `app-runtime`

Responsibilities:

- session lifecycle
- prompt construction
- planning loop
- tool invocation policy
- retries and stopping conditions

### `app-tools`

Responsibilities:

- typed tool definitions
- JSON schemas for model tool calling
- tool argument validation

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

### `app-security`

Responsibilities:

- sign-in integration
- secure local secret handling
- biometric gating

### `app-llm`

Responsibilities:

- OpenAI client wrapper
- retries
- rate-limit handling
- request and response logging

## Constraints To Accept Early

### Distribution posture

Treat this as a sideloaded prototype, not a product plan.

That means:

- no Play-first design compromises
- no requirement to justify the concept against store policy
- architecture decisions should optimize for getting a reliable demo working

### Security posture

Because this is not being distributed, security should be pragmatic rather than product-grade.

That means:

- prefer a tiny relay backend if convenient
- otherwise tolerate a development-only secret path for your own device
- still keep secrets and logs out of the repo

### Reliability over generality

The system should optimize for:

- predictable next actions
- easy replay and debugging
- explicit user approvals

It should not optimize for:

- open-ended autonomy
- multi-agent complexity
- broad tool ecosystems

### Narrower APIs are optional, not mandatory

In a product plan, a capability router that prefers narrower APIs before accessibility would be a strong default.

For this prototype, that is optional. If accessibility gets you to a working demo fastest, use it directly.

## Sources

- Android AccessibilityService: https://developer.android.com/reference/android/accessibilityservice/AccessibilityService
- Jetpack Compose: https://developer.android.com/compose
- Android foreground services: https://developer.android.com/develop/background-work/services/fgs
- WorkManager: https://developer.android.com/reference/androidx/work/WorkManager
- Android process lifecycle: https://developer.android.com/guide/components/activities/process-lifecycle
- Android SpeechRecognizer: https://developer.android.com/reference/android/speech/SpeechRecognizer
- Android TextToSpeech: https://developer.android.com/reference/android/speech/tts/TextToSpeech
- Android Credential Manager: https://developer.android.com/identity/credential-manager
- Android BiometricPrompt: https://developer.android.com/identity/sign-in/biometric-auth
- Android Room: https://developer.android.com/jetpack/androidx/releases/room
- Android DataStore: https://developer.android.com/datastore
- OpenAI API overview: https://developers.openai.com/api/reference/overview
- OpenAI API libraries: https://developers.openai.com/api/docs/libraries
- OpenAI Java SDK: https://github.com/openai/openai-java
- OpenAI realtime model docs: https://developers.openai.com/api/docs/models/gpt-realtime
- OpenAI Codex harness post: https://openai.com/index/unlocking-the-codex-harness/
- Codex App Server README: https://github.com/openai/codex/blob/main/codex-rs/app-server/README.md
