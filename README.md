# Cafes

A cafe visit photo log with an Android app and a browser app. Take a picture, save the visit with the cafe the app thinks you are at, and revisit the photos later.

## Current features

- Capture a cafe visit with a photo.
- Show the device's current location while the app is open.
- Find nearby cafes using OpenStreetMap data. The app selects a nearby cafe automatically when the location fix and candidate results are unambiguous; otherwise, the user can choose a result or enter a name.
- Add more photos to a visit from the system photo picker. Cards loop through the visit's photos with a fade transition.
- Archive and restore visits. Android cards can be swiped; the web app uses photo-area swipes on mobile and explicit archive controls on desktop.
- Search visits by cafe, address, manually added dish tags, or automatically recognized dishes in photos.
- Classify food photos on-device and show a per-visit processing indicator while dish tags are being generated. A stored model version lets a future model update trigger reclassification.
- Open visit details to review classification history, including timestamps, model version, elapsed time, detected labels and confidence, no-detection outcomes, processing errors, and a retry action. Web details open in a desktop dialog or a full-screen mobile view.
- Keep visit records and photos in local app storage.
- Use the web app in a modern browser, with geolocation, nearby OpenStreetMap lookup, photo selection/camera capture where supported, and browser-local visit storage.
- Adapt the web layout to mobile and desktop: mobile uses a single-column log with swipe-to-archive on photos, while desktop uses a multi-column card grid and visible archive controls. Card text can be selected.

## Project modules

The Gradle project is split by responsibility:

| Module | Responsibility |
| --- | --- |
| `:app` | Android application, manifest, startup activity, and navigation |
| `:feature:cafe-log` | Cafe log screen and its presentation state |
| `:core:data` | Local cafe log persistence |
| `:core:location` | Location, address, and nearby cafe provider facades and implementations |
| `:core:recognition` | Android on-device food-classification facade and model runtime |
| `:core:model` | Kotlin Multiplatform module; platform-neutral cafe visit, venue candidate, and position models live in `commonMain` |
| `:core:designsystem` | Shared Compose theme and app theme resource |
| `:webApp` | Kotlin/Wasm browser app using the shared cafe models |

The cafe-log feature depends on the core modules and the app shell depends on the feature. Location lookup, food recognition, and persistence sit behind module boundaries so their implementations can evolve independently.

## Technology stack

| Area | Stack |
| --- | --- |
| Language and build | Kotlin 2.3.20, Gradle 9.1.0, Android Gradle Plugin 9.0.1, JDK 17 |
| Android | Native Android app, Jetpack Compose UI with Material 3, minimum API 24, compile SDK 37 |
| Shared code | Kotlin Multiplatform `:core:model`, targeting Android and Kotlin/Wasm |
| Web | Compose Multiplatform 1.11.1, Kotlin/Wasm browser target, static HTML distribution |
| Location and place lookup | Google Play services Fused Location Provider; Android Geocoder; OpenStreetMap Overpass behind location/provider facades |
| Food tagging | Google AIY Food V1 TFLite model; LiteRT in Google Play services on Android and MediaPipe Tasks Vision in the browser |
| Android storage | App-private files for photos and SharedPreferences JSON for active and archived entries |
| Web storage | Browser `localStorage` for entries and compressed photo data |
| Web hosting | Static Nginx container behind Traefik; no application server or database |

`FoodTaggerFacade` and the location facades isolate provider APIs from the UI. `:core:model` holds the shared visit and food-tagging contracts. Android and web each provide their own inference and storage implementations. The web app stores visits in that browser's local storage and does not sync with Android or another browser.

## Build and run

Open the project directory in Android Studio and run the `app` configuration on an Android device or emulator. The project uses JDK 17, Kotlin 2.3.20, Gradle 9.1.0, Android Gradle Plugin 9.0.1, and requires Android SDK 37 for compilation. The app supports Android 7.0 (API 24) and newer.

To build a debug APK with [Android CLI](https://developer.android.com/tools/android-cli):

```sh
android build "$PWD?:app:assembleDebug"
```

The APK is written to:

```text
app/build/outputs/apk/debug/app-debug.apk
```

The application ID is `com.pratclot.cafes`.

## Web app

Run the browser app's development server with Android CLI:

```sh
android build "$PWD?:webApp:wasmJsBrowserDevelopmentRun"
```

Create a static production distribution with Android CLI:

```sh
android build "$PWD?:webApp:wasmJsBrowserDistribution"
```

The generated site is under `webApp/build/dist/wasmJs/productionExecutable`. Serve it over HTTPS (or use localhost during development) for browser geolocation to work. WebAssembly support is required. Cafe lookup uses OpenStreetMap Overpass; photos and visit records stay in that browser's local storage. Browser food classification loads MediaPipe Tasks Vision from jsDelivr the first time it is needed; the bundled model and photos are processed locally in the browser.

## Dish search and model

Dish tags are produced by Google's AIY Food V1 image classifier, a 2,023-dish classifier released under Apache 2.0. It classifies a cropped food image rather than detecting objects or reading text, so results can be wrong or missing. Its training data is skewed toward North American foods. Tags are suggestions, not ingredient or allergen information; users can add their own tags. Android runs the classifier through LiteRT in Google Play services. The model version is saved on each visit so changing the configured model version causes old photos to be classified again.

The model card and label map are available from [Google's Kaggle model page](https://www.kaggle.com/models/google/aiy/tfLite/vision-classifier-food-v1/1?tfhub-redirect=true). The model and label map are bundled in `core/recognition` for Android and the web app resources for static deployment.

## Location and cafe lookup

Location is read through Google Play services' fused location provider while the app is in use. A readable address is obtained through Android's `Geocoder`. Nearby cafe suggestions come from OpenStreetMap via the Overpass API, using a European endpoint when the location is in Europe and a global endpoint otherwise.

The location and cafe lookup implementations sit behind `LocationFacade`, `AddressLookupFacade`, and `NearbyCafeFacade` in `core/location/src/main/kotlin/com/pratclot/cafes/location/CafeLocationFacades.kt`. This keeps the UI independent of the current providers so they can be replaced later.

OpenStreetMap cafe coverage can vary by area. The app allows manual cafe selection when it cannot confidently choose a match. Internet access and location permission are needed for location and nearby lookup; photo logging itself is stored locally.

## Data and privacy

On Android, cafe entries are stored in app-private shared preferences, and image files are stored in app-private files. Photos selected from the photo picker are copied into app storage so visits do not depend on temporary picker access. Food classification runs locally on the device. On the web, entries and compressed photos are stored in browser local storage; classification runs locally after loading the MediaPipe runtime from jsDelivr. Nearby cafe searches send the device coordinates to the configured Overpass service. No account, cloud sync, or sharing between devices is implemented yet.

## Planned, not implemented

- Editing visit details.
- Syncing with another person's device, including iPhone support.
- Nearby device discovery to identify who joined a visit.
- Publishing a public release.
