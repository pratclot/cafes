# AGENTS.md

Guidance for AI coding agents working in the `cafes` repository. Keep this file up to date whenever module boundaries, dependency conventions, architecture, or build/deployment workflows change.

## Project Overview

`cafes` is a Kotlin/Compose application for logging cafe visits with camera capture, photo picking, automatic/manual location detection, and on-device/in-browser food tagging. It targets:
- **Android** (`:app`, `:feature:cafe-log`, `:core:*`)
- **Web (Kotlin/WasmJS)** (`:webApp`, `:core:model`, `:core:data`, `:core:passthrough`)

## Module Structure

- `:app` — Android application entry point (`MainActivity`), edge-to-edge setup, and theme/navigation host.
- `:feature:cafe-log` — Android feature module containing the main UI (`MainScreen.kt`), CameraX preview (`CameraPreview.kt`), Navigation 3 + `ListDetailSceneStrategy` (`CafeLogNavigation.kt`), `CafeLogViewModel`, and unit/UI tests.
- `:core:model` — Kotlin Multiplatform (`android`, `wasmJs`) domain models and platform boundary interfaces (`FoodTaggerFacade`, `CafeLocationFacades`, `LocationFacade`, `AddressLookupFacade`, `NearbyCafeFacade`) shared between Android and Web.
- `:core:passthrough` — Kotlin Multiplatform (`android`, `wasmJs`) dependency passthrough module exposing common cross-platform dependencies (e.g. `kotlinx-coroutines-core`) via `api(...)`.
- `:core:passthrough-android` — Android library passthrough module exposing `api(project(":core:passthrough"))` plus shared Android/Compose/Lifecycle/Navigation 3/Adaptive dependencies via `api(...)`.
- `:core:designsystem` — Android Material 3 theme, colors, and typography (`ComPratclotCafesTheme`).
- `:core:data` — Kotlin Multiplatform (`android`, `wasmJs`) data and shared state module containing `CafeLogRepository`, `CafeEntryListOps`, `InMemoryCafeLogRepository`, `CafeLogStateHolder` (`commonMain`), and Android `LocalCafeLogRepository` (`androidMain`).
- `:core:location` — Android location detection (`play-services-location`).
- `:core:recognition` — Android food tagging (`play-services-litert` / LiteRT).
- `:webApp` — Kotlin/WasmJS Compose Multiplatform web app (`CafeWebApp.kt`), browser interop (`BrowserInterop.kt`), and Web Worker food classification (`food-classifier-worker.js`).

## Dependency Management Conventions

- **Version Catalog**: Define all library/plugin versions and coordinates in `gradle/libs.versions.toml`.
- **Cross-Platform Shared Dependencies**: Expose common KMP dependencies as `api(...)` in `core/passthrough/build.gradle.kts` (`kotlin-multiplatform` + `android-kotlin-multiplatform-library`). Pure-Kotlin/KMP modules (`:webApp`, `:core:data`) depend on `project(":core:passthrough")`.
- **Android Shared Dependencies**: Expose shared Android/Compose/Navigation 3/Adaptive dependencies as `api(...)` in `core/passthrough-android/build.gradle.kts` (which re-exports `:core:passthrough`). Android UI/feature/service modules (`:app`, `:feature:cafe-log`, `:core:designsystem`, `:core:location`, `:core:recognition`) depend on `project(":core:passthrough-android")`.
- **Module-Specific Dependencies**: Keep specialized dependencies in their owning module only (e.g., `androidx-camera-*` and test dependencies in `:feature:cafe-log`, `play-services-location` in `:core:location`, `play-services-litert` in `:core:recognition`, `kotlinx-browser` in `:webApp`).

## Build, Test, and Deployment

- **Build Android APK + run unit tests + compile WasmJS**:
  ```bash
  ./gradlew :app:assembleDebug :feature:cafe-log:testDebugUnitTest :webApp:compileKotlinWasmJs
  ```
- **Build production Web distribution**:
  ```bash
  ./gradlew :webApp:wasmJsBrowserDistribution --rerun-tasks
  ```
- **Deploy Web app to VM**:
  ```bash
  ./deploy/publish-vm.sh
  ```
  (Uses `DEPLOY_TARGET` defaulting to `oracle`, `CAFES_HOST` defaulting to `cafes.164.152.20.248.nip.io`, and `CAFES_REMOTE_DIR` defaulting to `/home/ubuntu/cafes`.)
- **Environment note**: Gradle commands that write to `/home/chief/.android` and SSH/git network operations require running outside the read-only/network-isolated sandbox.

## Code & UI Guidelines

- Android navigation uses Navigation 3 (`NavDisplay`, `rememberNavBackStack`, `ListDetailSceneStrategy`) with `EdgeToEdge` and IME inset handling.
- Web food recognition runs in a Web Worker (`webApp/src/wasmJsMain/resources/food-classifier-worker.js`) so the UI thread stays responsive.
- Android and Web UI are currently separate (`:feature:cafe-log` vs. `:webApp`); when adding shared domain models, place them in `:core:model`, and prefer extracting platform-agnostic logic into KMP modules when practical.
