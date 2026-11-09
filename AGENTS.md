# Repository Guidelines

## Project Overview

Fcitx5 for Android ports the Fcitx5 input-method framework and native engines to Android. The Kotlin application provides the keyboard, candidate/preedit UI, settings, themes, clipboard, and editor integration; C++ handles engine execution. Separate plugin APKs supply additional engines and services.

## Architecture & Data Flow

- `FcitxApplication` initializes preferences, themes, logging, and storage. Preserve direct-boot/device-protected storage behavior.
- `FcitxDaemon` owns the engine. Use a lifecycle-owned `FcitxConnection`; disconnect on destruction. Do not instantiate `core/Fcitx` directly or retain its `FcitxAPI` outside a connection callback.
- Input flow: keyboard `KeyAction` or Android key event → `FcitxInputMethodService.postFcitxJob` → `FcitxConnection` → JNI (`native-lib.cpp`) → `androidfrontend` → native engine.
- Output flow: native callbacks → `Fcitx.handleFcitxEvent` / `FcitxEvent` → cached state and `eventFlow` → service/editor `InputConnection` and `InputView` component broadcasts.
- JNI event IDs, argument types, constructor signatures, and Kotlin decoders form one contract: update both sides together. Preserve physical-key correlation indexes; timestamps cannot uniquely pair key events.
- Native libraries expose headers/libraries through Android Prefab. Gradle conventions install configuration, translations, and prebuilt assets and generate descriptors; runtime `DataManager.sync()` merges/synchronizes app and plugin assets before engine initialization. Descriptor formats must agree between build logic and runtime.

## Key Directories

| Path | Purpose |
| --- | --- |
| `app/src/main/java/org/fcitx/fcitx5/android/` | App code: `core/` JNI API/events, `daemon/` engine ownership, `input/` IME/UI, `data/` preferences/themes/storage, `ui/` settings |
| `app/src/main/cpp/` | JNI bridge and Android-specific frontend, keyboard, and addon loader |
| `lib/fcitx5`, `lib/libime`, `lib/fcitx5-chinese-addons`, `lib/fcitx5-lua` | Android wrappers around native upstream submodules |
| `lib/common`, `lib/plugin-base` | Shared AIDL/plugin IPC and plugin metadata/About UI |
| `plugin/` | Engine/service plugin APK modules |
| `codegen/` | KSP/KotlinPoet processors generating key and scancode mappings |
| `build-logic/convention/src/main/kotlin/` | Shared Android/native build conventions, asset installation, signing, and version defaults |
| `app/src/test`, `app/src/androidTest`, `app/schemas` | JVM tests, device tests, and Room schema snapshots |

Treat upstream submodules as separate projects. Do not edit generated `build/`, `.cxx/`, key-mapping output, `src/main/assets/usr/`, or generated `descriptor.json`; change their source inputs. Room schema updates are distinct from disposable build output.

## Development Commands

Run from the repository root with the Gradle wrapper. Initialize native sources and prebuilts first:

```sh
git submodule update --init --recursive
./gradlew :app:assembleDebug -PbuildABI=arm64-v8a
./gradlew :app:installDebug -PbuildABI=arm64-v8a
./gradlew :app:assembleRelease -PbuildABI=arm64-v8a
./gradlew :assembleReleasePlugins
./gradlew :app:testDebugUnitTest
./gradlew :app:testDebugUnitTest --tests 'org.fcitx.fcitx5.android.PanguFormatterTest'
./gradlew :app:lintDebug
./gradlew :app:connectedDebugAndroidTest
```

Choose an ABI matching the device/emulator; `buildABI` accepts comma-separated values. Without restriction, native APK builds split across four ABIs. Installation/device tests require an authorized `adb` device. Debug adds `.debug` to the app ID; release enables R8 minification and resource shrinking. Release signing requires configuration; an unsigned release APK cannot be installed.

CI builds `:app:assembleRelease` and `:assembleReleasePlugins`. Test/lint commands above are standard Android Gradle entry points, not an assertion that CI runs full test or lint suites.

## Code Conventions & Common Patterns

- Follow official Kotlin style, four-space indentation, surrounding code, and `.idea/codeStyles/`. Preserve file-specific SPDX/license headers. No first-party ktlint, Spotless, Detekt, or standalone formatter configuration was found.
- UI uses native Android Views, Splitties Kotlin DSL, and RecyclerView—not Compose. Follow existing `*Ui`, `*Component`, and view-holder patterns.
- Input UI dependency injection uses `org.mechdancer.dependency`: `DynamicScope`, `UniqueComponent`, `Dependent`, and typed `manager` delegates. Register dependencies in `InputView.setupScope()` before component use; use existing broadcasts rather than a second event mechanism.
- Engine work is confined to `FcitxDispatcher`'s `fcitx-main` thread. Use existing suspend/readiness helpers. `runOnReady` waits, `runIfReady` skips when unavailable, and `runImmediately` blocks. Preserve the sequential `postFcitxJob` queue: ordinary coroutine launches can interleave across suspensions and reorder input.
- Bind collectors/jobs to lifecycle scopes; cancel view collectors on detach. Engine lifecycle is explicit (`STOPPED → STARTING → READY → STOPPING → STOPPED`). `eventFlow` is a non-replaying, bounded `SharedFlow`, not durable state; use existing caches to restore UI state.
- Preferences use `AppPrefs` and `ManagedPreference` delegates. Listeners are weakly held: retain references and follow registration/unregistration and existing `@Keep` patterns so release R8 does not remove required listeners.
- Settings fragments share `MainViewModel` through `activityViewModels()`; follow its connection/state ownership.
- Log with `Timber` in Kotlin and `FCITX_*` in C++. Follow boundary-specific error handling; do not introduce a universal swallowed-exception/fallback convention. Preserve manifest IPC permissions and editor-sensitive input behavior.

## Important Files

- `app/src/main/AndroidManifest.xml`: application, IME, launcher, direct-boot and permission boundaries.
- Under `app/src/main/java/org/fcitx/fcitx5/android/`: `FcitxApplication.kt`, `input/FcitxInputMethodService.kt`, `input/InputView.kt`, `daemon/FcitxDaemon.kt`, `core/Fcitx.kt`, `core/FcitxEvent.kt`, and `core/data/DataManager.kt` are primary entry points/contracts.
- `app/src/main/cpp/native-lib.cpp`, `jni-utils.h`, and `androidfrontend/androidfrontend.cpp`: native API/event bridge.
- `settings.gradle.kts`, `gradle/libs.versions.toml`, `gradle.properties`, and build-logic `Versions.kt` / `ProjectExtensions.kt`: module graph, tool versions, build overrides, and signing.
- `app/build.gradle.kts`: app dependencies, KSP, Room schema output, native targets, asset selection, and test runner.
- `README.md`: central setup/behavior documentation; `.github/workflows/`: actual CI workflows. Translations are mapped in `transifex.yml` / `transifex-config.ini`; Android base strings are in `res/values/strings.xml`, native catalogs in `app/src/main/cpp/po/`.

## Runtime/Tooling Preferences

- Use `./gradlew`, not a system Gradle or a JavaScript package manager. Wrappers pin Gradle **9.6.1**; the catalog pins AGP **9.4.1**, Kotlin **2.4.10**, and KSP **2.3.10**.
- CI/Nix select **JDK 17**; Android Java/Kotlin bytecode targets **11**. These are different settings. No explicit build JDK toolchain pin was found; check compatibility before substituting a newer host JDK.
- Current defaults in build-logic `Versions.kt`: compile/target SDK **36**, minimum API **23**, Build Tools **36.1.0**, NDK **28.0.13004108**, CMake **3.31.6**. Prefer this file over stale README dependency-version bullets.
- Host native prerequisites include **extra-cmake-modules** and **GNU gettext ≥ 0.20** (`msgfmt`). Recursive submodules include ABI-specific prebuilt dependencies; Gradle alone does not fetch them.
- Supported overrides include `NDK_VERSION` / `-PndkVersion=…`, `CMAKE_VERSION` / `-PcmakeVersion=…`, `BUILD_TOOLS_VERSION` / `-PbuildTools=…`, and `BUILD_ABI` / `-PbuildABI=…`. Nonblank environment values take precedence over properties. Prefer supported local overrides to editing shared configuration for machine-specific problems.
- Release signing uses `SIGN_KEY_FILE` or `SIGN_KEY_BASE64`, plus `SIGN_KEY_PWD` and `SIGN_KEY_ALIAS` (camelCase Gradle properties also exist). Never commit credentials or change signing identity to force an upgrade; preserve device data.
- Optional reproducible environment: `nix develop .#noAS` (without Studio) or `nix develop` (with Studio). The shell supplies native/Android tools and rewrites `local.properties`; that file is machine-local.

## Testing & QA

- JVM tests use **JUnit 4**; device tests use **AndroidJUnitRunner** and AndroidX Test. Existing tests cover Unicode/UTF-16 spacing, input sessions, theme migration, escaping, native engine behavior, editor integration, and glass rendering.
- Prefer focused classes/methods for changed behavior, then proportionate integration checks. Device rendering/editor changes need a real device/emulator check; JVM tests alone do not establish visual correctness.
- Reflection-based editor regression harnesses require the README's invocation after installing debug app/test APKs:

```sh
adb shell am instrument --no-hidden-api-checks -w \
  -e class org.fcitx.fcitx5.android.GlassPanelTest,org.fcitx.fcitx5.android.PanguInputConnectionTest \
  org.fcitx.fcitx5.android.debug.test/androidx.test.runner.AndroidJUnitRunner
```

The hidden-API flag is for test-harness injection, not a keyboard runtime requirement. Device tests should restore preferences and input methods and perform View work on the main thread, following existing tests.

No numerical coverage threshold or enforced test-per-change policy was found. Upstream native tests exist but Android wrappers default `ENABLE_TEST` to `OFF`; app Gradle tests do not imply those suites ran. Report exact checks exercised and any environment blockers; do not claim CI or visual coverage that was not observed.
