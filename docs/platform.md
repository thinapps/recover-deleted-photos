# Platform

Recover Deleted Photos is aligned with the current Android 16 build and runtime requirements.

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

App-owned layout dimensions, component sizes, spacing, shape measurements, elevations, touch targets, and explicit text sizes are centralized in `app/src/main/res/values/dimens.xml`.

Layouts, styles, shape drawables, and vector intrinsic sizes reference these named resources instead of repeating hardcoded `dp` and `sp` values. Numeric values that are not Android dimensions, such as vector viewport coordinates, path data, ratios, weights, and alpha values, remain inline.

## Interaction feedback

The app requests standard Android haptic feedback for intentional, low-frequency actions. These include the Home screen scan and recovered-media buttons; Scan cancellation, back navigation, permission, retry, and Go Home actions; Results recovery, layout changes, recovery outcomes, and back navigation; recovered-viewer back navigation; failed recovered-file opening; and opening the Privacy Policy dialog.

The Home screen media-type radio buttons remain silent because they are routine option changes. Results sorting remains silent because it is a standard dropdown selection. Successful recovered-file opening remains silent because the launched viewer already confirms the action.

Privacy Policy dialog dismissal also remains silent. The dialog's Close button, outside-tap dismissal, and system-back dismissal intentionally do not request haptic feedback, keeping equivalent dismissal methods consistent and avoiding unnecessary vibration.

## Android 16 behavior

Apps targeting API 36 run edge-to-edge without the previous opt-out. `MainActivity` enables edge-to-edge explicitly and applies status bar, navigation bar, and display-cutout insets to the activity root so the toolbar and fragment content remain fully accessible.

Navigation continues to use AndroidX Navigation and `OnBackPressedDispatcher`. The Scan and Results screens keep their custom cleanup behavior without disabling predictive back support at the manifest level.

## Permissions

The app does not add or broaden permissions beyond the scoped media access declared in the manifest. Android 14 selected-media access and re-selection UI remain deferred to a later permission-focused release.
