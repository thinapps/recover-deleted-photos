# Platform

Recover Deleted Photos version 1.2.1 is aligned with the current Android 16 build and runtime requirements.

## Android support

- minimum SDK: Android 5.0 / API 21
- scanning interface: Android 13 / API 33 and later
- compile SDK: Android 16 / API 36
- target SDK: Android 16 / API 36

## Build toolchain

- Android Gradle Plugin 8.10.1
- Kotlin 2.2.21
- Gradle 8.11.1 in the release workflow
- Java and Kotlin JVM target 17
- R8 minification and resource shrinking remain disabled

The release workflow installs the Android 36 platform, builds a signed release bundle, verifies its signature, and names the artifact from the app version.

## UI resources

Version 1.2.1 centralizes app-owned layout dimensions, component sizes, spacing, shape measurements, elevations, touch targets, and explicit text sizes in `app/src/main/res/values/dimens.xml`.

Layouts, styles, shape drawables, and vector intrinsic sizes reference these named resources instead of repeating hardcoded `dp` and `sp` values. Numeric values that are not Android dimensions, such as vector viewport coordinates, path data, ratios, weights, and alpha values, remain inline.

## Android 16 behavior

Android 16 enforces edge-to-edge display for apps targeting API 36. `MainActivity` enables edge-to-edge explicitly and applies status bar, navigation bar, and display-cutout insets to the activity root so the toolbar and fragment content remain fully accessible.

Navigation continues to use AndroidX Navigation and `OnBackPressedDispatcher`. The Scan and Results screens keep their custom cleanup behavior without disabling predictive back support at the manifest level.

## Permissions

Version 1.2.1 does not add, remove, or broaden any manifest permission. Android 14 selected-media access and re-selection UI remain deferred to a later permission-focused release.
