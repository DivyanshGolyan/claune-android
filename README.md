# Claune Android

Claune Android is a fresh-start prototype for a phone-native Android agent.

The goal is not distribution. The goal is to build something fast, inspectable, and reliable enough to demo in person or record in a video.

The current direction is:

- Kotlin-first Android app and services
- thin custom agent runtime instead of a generic coding-agent harness
- AccessibilityService-based phone control
- voice-first interaction
- cloud-backed LLM reasoning from the phone
- one active session and one tool call per turn in v1
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
