# Cafes Hardening & Cleanup Summary (7-PR Plan)

This document summarizes the 7 incremental commits applied to harden the `cafes` Android + Kotlin/WasmJS codebase, CI, persistence, media handling, UI architecture, and deployment.

---

## PR 1 — Repo Hygiene, Dead Code Removal & Module Decoupling
- **Removed unused files & assets**:
  - Deleted untracked `IMG_20261017_171835611 (1).jpg`, empty `models/` directory, and unused Web copies `webApp/src/wasmJsMain/resources/food_v1.tflite` & `food_labels.csv` (Web uses `food_siglip_embeddings.json` + Transformers.js).
- **Removed dead starter-template code**:
  - Deleted `DataRepository` / `DefaultDataRepository` (`CafeLogRepository.kt`), `MainScreen(data: List<String>, ...)` (`MainScreen.kt`), `MainScreenViewModel.kt`, `MainScreenViewModelTest.kt`, `MainScreenTest.kt`, and unused `browserFormatDate` (`BrowserInterop.kt`).
- **Decoupled non-UI Android modules**:
  - `:core:location` now depends on `:core:passthrough` + `androidx.core:core-ktx` instead of `:core:passthrough-android`.
  - `:core:recognition` now depends on `:core:passthrough` instead of `:core:passthrough-android`.
- **Updated `AGENTS.md`** to reflect the updated module dependency conventions.

---

## PR 2 — Static Analysis, CI Pipeline, Logging Hook & `:core:data` Tests
- **Static analysis & CI (no git hooks)**:
  - Added `ktlint` (`org.jlleitschuh.gradle.ktlint`) and `detekt` (`io.gitlab.arturbosch.detekt`) in `build.gradle.kts`, `gradle/libs.versions.toml`, `.editorconfig`, and `config/detekt/detekt.yml`.
  - Added GitHub Actions CI workflow at `.github/workflows/ci.yml` running `ktlintCheck`, `detekt`, `:app:lintDebug`, `:core:data:testAndroidHostTest`, `:feature:cafe-log:testDebugUnitTest`, `:app:assembleDebug`, and `:webApp:wasmJsBrowserDistribution`.
- **Multiplatform logging hook**:
  - Added `CafeLog` (`core/model/src/commonMain/kotlin/com/pratclot/cafes/core/model/CafeLog.kt`) with `androidMain` (`android.util.Log` with JVM-test fallback) and `wasmJsMain` implementations, wired into caught exceptions in `CafeLogStateHolder`.
- **`:core:data` `commonTest` harness**:
  - Enabled `withHostTest {}` in `core/data/build.gradle.kts` and added `CafeLogRepositoryTest.kt` and `CafeLogStateHolderTest.kt` covering `CafeEntryListOps`, `InMemoryCafeLogRepository`, `chooseUnambiguousCafe`, and `CafeLogStateHolder`.

---

## PR 3 — State Integrity, Concurrency & Lifecycle Fixes
- **`CafeLogStateHolder` concurrency & ID-based mutations**:
  - Added `repositoryMutex` around all repository read-modify-write + `_uiState` updates.
  - Changed `addManualTag`, `archiveEntry`, `restoreEntry`, and `retryClassification` to accept `entryId: String` and resolve the latest entry inside `repositoryMutex`.
  - Updated `CafeLogViewModel`, `MainScreen`, `CafeWebApp`, and `CafeLogViewModelTest`.
- **Bounded classification & cleanup**:
  - Added `Semaphore(2)` and `try ... finally` in `schedulePendingClassifications` so `taggingIds` is always cleared even on cancellation or error.
  - Guarded `onNewPosition` with `positionMutex`, cancelled prior `cafeSearchJob` before launching a new search, and cancelled `locationJob`, `locationTimeoutJob`, and `cafeSearchJob` in `close()`.
- **Facade & coroutine cancellation fixes**:
  - Guarded `AndroidFoodTaggerFacade.classify()` / `close()` with `isClosed` and `RejectedExecutionException` handling.
  - Rethrew `CancellationException` and added `continuation.invokeOnCancellation { future.cancel(false) }` in `CameraPreview.kt`.
  - Added `invokeOnCancellation` guards in `BrowserServices.kt` / `BrowserFoodTaggerFacade.kt` and returned `Result.failure` on `"!ERROR"` in `OpenStreetMapBrowserCafeFacade.findNearby`.

---

## PR 4 — Persistence & Storage Hardening
- **`suspend` repository & `kotlinx.serialization`**:
  - Made `CafeLogRepository` methods `suspend` across all implementations (`InMemoryCafeLogRepository`, `LocalCafeLogRepository`, `BrowserCafeLogRepository`, `FakeCafeLogRepository`).
  - Annotated `CafeEntry`, `FoodLabel`, `ClassificationRun`, and `CafeLogSnapshot` with `@Serializable`.
- **Android atomic JSON file storage (`LocalCafeLogRepository`)**:
  - Replaced `SharedPreferences` JSON-building with `Mutex`-guarded atomic `cafe_log.json` writes in `context.filesDir` (`cafe_log.json.tmp` -> `cafe_log.json`).
  - Added legacy `SharedPreferences("cafe-log")` migration with per-entry `runCatching`, explicit missing-photo sanitization (preserving entry history while filtering missing files), and orphan `cafe-*` file cleanup.
- **UUIDs & Web IndexedDB storage**:
  - Switched `saveCapturedBitmap`, `savePickedPhotos`, and `CafeLogStateHolder` `idGenerator` to UUID-based IDs.
  - Moved Web photo blobs (`data:image/...`) to IndexedDB (`cafes-photos-db` / `idb://photo-...`) with `kotlinx.serialization` snapshot persistence in `localStorage` and automatic migration from the legacy `|`-delimited format.

---

## PR 5 — Image Decoding, Threading & Memory Management
- **Background camera capture & bitmap recycling**:
  - Replaced main-thread capture executor in `MainScreen.kt` with a dedicated single-thread `ExecutorService` shut down in `DisposableEffect`.
  - Recycled captured bitmaps in `finally` after compression in `CafeLogViewModel.saveCapturedBitmap`.
- **Subsampled EXIF-aware decoding & caching**:
  - Added shared `decodeSampledBitmap(path, maxDimensionPx)` in `core/model/src/androidMain/.../AndroidBitmapDecoder.kt` using `inSampleSize` and `ExifInterface`.
  - Wired `decodeSampledBitmap` into `AndroidFoodTaggerFacade` (`512px`) and `AsyncLocalPhoto` (`1024px`) with a `16 MiB` `LruCache`.
- **Web photo decoding & tensor disposal**:
  - Moved `BrowserPhoto` Base64 decoding into `produceState` on `Dispatchers.Default` with a bounded 32-entry cache.
  - Added `.dispose()` cleanup for `pixelTensor` and `visOut` tensors in `food-classifier-worker.js`.

---

## PR 6 — UI Decomposition, Localization, Accessibility & Web Dark Theme
- **UI decomposition & shared helpers**:
  - Split `MainScreen.kt` into `CafeLogPhotoHelpers.kt` (including shared `copyPickedPhotosToFilesDir` saving `.jpg`), `CafeLogLocationComponents.kt`, and `CafeLogVisitDetailScreen.kt`.
  - Split `CafeWebApp.kt` into `CafeWebPhotoComponents.kt` and `CafeWebVisitComponents.kt`.
  - Removed redundant outer `NavDisplay` in `app/.../Navigation.kt` and deleted `NavigationKeys.kt`.
- **Typed status/error state, accessibility & formatting**:
  - Replaced hardcoded `refreshMessage` / `cafeSearchError` strings in `CafeLogStateHolder` with `RefreshStatusMessage` and `CafeSearchError` enums mapped to `core/ui` `strings.xml`.
  - Added `contentDescription` on `AsyncLocalPhoto` and `SharedCompactLocationBar` / `CurrentLocationBar` refresh buttons.
  - Enabled dark theme support in `CafeWebApp.kt` by replacing hardcoded light-mode colors with `MaterialTheme.colorScheme.*`, and removed the redundant `browserUpdateCameraBounds` call in `onGloballyPositioned`.
  - Wrapped `SimpleDateFormat` in `ThreadLocal` in `PlatformFormatting.android.kt`.

---

## PR 7 — Security, Release Build & Deployment Hardening
- **Android release & backup hardening**:
  - Enabled `isMinifyEnabled = true` and `isShrinkResources = true` in `app/build.gradle.kts` with `app/proguard-rules.pro`.
  - Wired `android:fullBackupContent="@xml/backup_rules"` and `android:dataExtractionRules="@xml/data_extraction_rules"` in `AndroidManifest.xml`.
- **Network & worker hardening**:
  - Bounded Overpass HTTP response reads to `2 MiB` in `OpenStreetMapCafeFacade`.
  - Added `12s` `AbortController` timeout and European Overpass mirror fallback in `browserFindNearbyCafes`.
  - Pinned Transformers.js worker import to the exact `3.3.3` `transformers.min.js` URL.
- **Nginx security headers & deployment**:
  - Added `deploy/nginx.conf` with `X-Content-Type-Options`, `X-Frame-Options`, `Referrer-Policy`, `Permissions-Policy`, and a WasmJS-compatible `Content-Security-Policy` (`'wasm-unsafe-eval'`).
  - Mounted `deploy/nginx.conf` in `deploy/compose.yaml` and updated `deploy/publish-vm.sh` to upload `nginx.conf` and prune old releases (keeping latest 5).

---

## Follow-up — Typed WasmJS Interop & ES Module Extraction
- **Refactored `webApp/src/wasmJsMain/kotlin/com/pratclot/cafes/web/BrowserInterop.kt`**:
  - Replaced raw `js("...")` calls for `localStorage`, `encodeURIComponent`/`decodeURIComponent`, `Date.now()`, and `crypto.randomUUID()` with typed `kotlinx.browser.window` and Kotlin `external` declarations.
  - Extracted IndexedDB photo storage (with a single shared `openPhotoDb`/`loadPhotoBlob` helper), `<input type="file">` photo picking, `getUserMedia` camera preview/capture, geolocation, and Overpass `fetch` (`AbortController` timeout + mirror fallback) into `webApp/src/wasmJsMain/resources/browser-interop.mjs`, bundled by webpack via `@JsModule("./browser-interop.mjs")` + `external fun`.
