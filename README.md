# Cafes

An Android app for keeping a photo log of cafe visits. Take a picture, save the visit with the cafe the app thinks you are at, and revisit the photos later.

## Current features

- Capture a cafe visit with a photo.
- Show the device's current location while the app is open.
- Find nearby cafes using OpenStreetMap data. The app selects a nearby cafe automatically when the location fix and candidate results are unambiguous; otherwise, the user can choose a result or enter a name.
- Add more photos to a visit from the system photo picker. Cards loop through the visit's photos with a fade transition.
- Swipe a visit left to archive it. Archived visits are available from the archive view and can be restored by swiping left there.
- Keep visit records and photos in local app storage.

## Project modules

The Gradle project is split by responsibility:

| Module | Responsibility |
| --- | --- |
| `:app` | Android application, manifest, startup activity, and navigation |
| `:feature:cafe-log` | Cafe log screen and its presentation state |
| `:core:data` | Local cafe log persistence |
| `:core:location` | Location, address, and nearby cafe provider facades and implementations |
| `:core:model` | Shared cafe visit, venue candidate, and position models |
| `:core:designsystem` | Shared Compose theme and app theme resource |

The cafe-log feature depends on the core modules and the app shell depends on the feature. Provider code and local storage stay behind their module boundaries, so they can evolve independently.

## Build and run

Open the project directory in Android Studio and run the `app` configuration on an Android device or emulator. The project uses JDK 17, Android Gradle Plugin 9.0.1, and requires Android SDK 37 for compilation. The app supports Android 7.0 (API 24) and newer.

To build a debug APK with [Android CLI](https://developer.android.com/tools/android-cli):

```sh
android build "$PWD?:app:assembleDebug"
```

The APK is written to:

```text
app/build/outputs/apk/debug/app-debug.apk
```

The application ID is `com.pratclot.cafes`.

## Location and cafe lookup

Location is read through Google Play services' fused location provider while the app is in use. A readable address is obtained through Android's `Geocoder`. Nearby cafe suggestions come from OpenStreetMap via the Overpass API, using a European endpoint when the location is in Europe and a global endpoint otherwise.

The location and cafe lookup implementations sit behind `LocationFacade`, `AddressLookupFacade`, and `NearbyCafeFacade` in `location/CafeLocationFacades.kt`. This keeps the UI independent of the current providers so they can be replaced later.

OpenStreetMap cafe coverage can vary by area. The app allows manual cafe selection when it cannot confidently choose a match. Internet access and location permission are needed for location and nearby lookup; photo logging itself is stored locally.

## Data and privacy

Cafe entries are stored in app-private shared preferences, and image files are stored in app-private files. Photos selected from the photo picker are copied into app storage so visits do not depend on temporary picker access. Nearby cafe searches send the device coordinates to the configured Overpass service. No account, cloud sync, or sharing between devices is implemented yet.

## Planned, not implemented

- Editing visit details.
- Syncing with another person's device, including iPhone support.
- Nearby device discovery to identify who joined a visit.
- Publishing a public release.
