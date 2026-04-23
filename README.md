# Claune Android

Claune Android is a prototype Android app for a phone-control agent. The user gives it a task by voice or text, and the app runs an agent on the phone that observes and controls the real device through Android accessibility APIs.

This repo is for local development, live demos, and debugging on a known Android 12+ device. It is not a distribution build.

## Current state

- Jetpack Compose app with a soft-kraft UI, a session list, a session detail screen, and settings.
- Voice input through Android `SpeechRecognizer`, plus typed input.
- API key storage in app settings, backed by DataStore.
- Foreground service for active agent work.
- AccessibilityService bridge for phone observation and actions.
- Accessibility overlay for status, steering, stopping, and replying while Claune is working over other apps.
- Persistent session history through the vendored `pi-agent-kotlin` session model.
- One active foreground run at a time. A user can keep a session and send later follow-up tasks into it.
- Model execution backed by vendored `pi-agent-kotlin` and Anthropic.
- `execute_script` as the only model-facing phone-control tool.
- Terminal and user-decision tools for `complete_task`, `block_task`, and `question`.
- Memory reflection after completed or blocked turns, with `read_memory` and `edit_memory` tools for durable updates.
- Local run artifacts under app storage: prompts, snapshots, script calls, agent messages, events, and memory-reflection output.

## Boundaries

- This is not a Play Store build.
- It is not a broad personal assistant.
- It is not multi-agent.
- It does not have production-grade auth, safety, or account management.
- It does not mirror the live phone. The user sees the real phone; Claune is the control and status layer.

## Repository layout

- `android/app`: the Android app, foreground service, accessibility service, overlay, runtime shell, UI, tests, and local storage.
- `android/vendor/pi-agent-kotlin/pi-ai-core`: vendored AI model/provider primitives.
- `android/vendor/pi-agent-kotlin/pi-agent-core`: vendored agent runtime primitives.
- `android/vendor/pi-agent-kotlin/pi-coding-agent-core`: vendored session, compaction, and transcript model used by Claune sessions.
- `docs/`: architecture and stack notes. Some of these still describe the earlier Koog plan, so treat the code and this README as current until those notes are refreshed.

## Requirements

- macOS or Linux with Android platform tools.
- JDK 17.
- Android Studio or the Android SDK installed locally.
- A connected Android device or emulator. Current testing targets Android 12 / API 31 or newer.
- An Anthropic API key.
- Accessibility access enabled for Claune before phone control will work.
- Microphone permission if you want voice input.

## Setup

Create `android/local.properties` if it does not exist, then add your local Android SDK path and, optionally, a development API key:

```properties
sdk.dir=/path/to/android/sdk
claune.anthropicApiKey=sk-ant-...
```

You can also add or replace the API key from the app's Settings screen. The build still reads `claune.anthropicApiKey` as a local development default.

Install a debug build:

```sh
cd android
./gradlew installDebug
```

On the device:

1. Open Claune.
2. Add the API key in Settings if it was not provided through `local.properties`.
3. Open Android Accessibility settings from the app and enable Claune.
4. Grant microphone permission when prompted, if you use voice input.

Avoid force-stopping the app during live accessibility testing. On this device setup, force-stop can clear or disrupt the accessibility-service state.

## Common commands

```sh
cd android
./gradlew formatCode
./gradlew qualityCheck
./gradlew testDebugUnitTest
./gradlew check
./gradlew assembleDebug
./gradlew installDebug
```

Notes:

- `qualityCheck` runs ktlint checks and Android lint.
- `check` also runs the app module's configured checks.
- `assembleDebug` and `installDebug` depend on `testDebugUnitTest` in this repo, so a debug build also runs unit tests.

## Debugging on a device

Start the app with a goal:

```sh
adb shell "am start -n com.divyanshgolyan.claune.android/.ui.MainActivity --es extra_autostart_goal 'open settings and tell me what you see'"
```

Show the overlay without starting an agent run:

```sh
adb shell "am start -n com.divyanshgolyan.claune.android/.ui.MainActivity --ez extra_debug_overlay true"
```

Check whether Claune's accessibility service is enabled:

```sh
adb shell settings get secure enabled_accessibility_services
```

If Droidrun Portal is installed on the same device, its content provider is useful for reading the current phone state while debugging:

```sh
adb shell content query --uri content://com.droidrun.portal/phone_state
```

## Current agent contract

The model should observe and act through `execute_script`. The JavaScript host exposes the `claune` API for phone observation, tapping, typing, scrolling, back/home, and waiting for UI state. The model should not invent raw Android objects or stale element ids.

When a turn ends, the model records one terminal outcome through a tool call:

- `complete_task` after the requested outcome is verified on the phone.
- `block_task` when progress is impossible, unsafe, or only partially complete.
- `question` when the run needs a user decision. The overlay presents 1 to 3 options and also allows custom input.

Completed or blocked turns do not close the user-owned session by themselves. The overlay and foreground session can stay up so the user can continue, steer, or stop explicitly.

## Status

This is a personal prototype repo with no public support process yet. There is no root license file at the moment; the vendored `pi-agent-kotlin` subtree keeps its own license and changelog.

## Docs

- [V1 architecture](./docs/v1-architecture.md)
- [Recommended stack](./docs/recommended-stack.md)
