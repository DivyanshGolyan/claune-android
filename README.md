# Claune Android

Claune Android is a fresh-start prototype for a phone-native Android agent.

The goal is not distribution. The goal is to build something fast, inspectable, and reliable enough to demo in person or record in a video.

The current direction is:

- Kotlin-first Android app and services
- Koog-backed agent loop inside an app-owned runtime shell
- Kotlin host runtime with a JS script execution layer
- AccessibilityService-based phone control
- voice-first interaction
- cloud-backed LLM reasoning from the phone
- one active session in v1
- one configured model tool in v1: `execute_script`
- Android 12 demo-device posture for the current prototype scaffold

Repository layout:

- `docs/` for architecture and stack decisions
- `android/` for the Gradle Android project scaffold

Quality checks:

- `cd android && ./gradlew qualityCheck` runs Kotlin formatting checks and Android lint
- `cd android && ./gradlew formatCode` auto-formats Kotlin sources with ktlint

Docs:

- [Recommended Stack](./docs/recommended-stack.md)
- [V1 Architecture](./docs/v1-architecture.md)
