# Permissions

Recover Deleted Photos version 1.2.2 targets Android 16 while keeping its scanning interface limited to Android 13 and later.

## Declared permissions

The manifest declares:

- `READ_MEDIA_IMAGES` for photos on Android 13+
- `READ_MEDIA_VIDEO` for videos on Android 13+
- `READ_MEDIA_AUDIO` for audio on Android 13+
- `READ_EXTERNAL_STORAGE` with `maxSdkVersion="32"` as a legacy declaration

Version 1.2.2 does not add, remove, or broaden any manifest permissions.

The app does not currently declare `READ_MEDIA_VISUAL_USER_SELECTED` for Android 14+ Selected Photos Access.

The Home and Scan screens block scanning below Android 13, so the legacy permission is not currently used by the active scan flow.

## In-app privacy policy

Version 1.2.2 adds a compact Privacy Policy link below the Home footer. It opens a Material dialog inside the app and does not require a browser, another screen, or Internet permission.

The policy describes the current local MediaStore scan, temporary in-memory results, recovery copies, recovered-file opening behavior, disabled Android cloud backup, and the absence of accounts, advertising, analytics, or a ThinApps-operated cloud service.

## Separate media permissions

Photos, videos, and audio use separate Android permissions. Android can show one combined dialog for image and video access only when both permissions are requested together.

This app requests only the selected type:

- Photos requests `READ_MEDIA_IMAGES`
- Videos requests `READ_MEDIA_VIDEO`
- Audio requests `READ_MEDIA_AUDIO`

Photos and videos are therefore not requested together by the current app.

## Home screen flow

The Home screen requests one permission through `ActivityResultContracts.RequestPermission`.

1. The user selects Photos, Videos, or Audio.
2. The app checks the permission for that type.
3. When granted, the app opens the Scan screen.
4. Otherwise, the first attempt for that permission launches the Android permission dialog.
5. After that same permission is denied, its main action opens the app's Android settings page.

The Home screen tracks requested permissions separately. Denying Photos does not prevent the app from normally requesting Videos or Audio for the first time.

The request history is temporary and resets when the Home view is destroyed. It is not stored between app sessions.

The Home screen refreshes its button label in `onResume`, so returning from Android settings immediately reflects whether the selected permission is now granted.

## Scan screen flow

The Scan screen checks the selected permission before scanning and again immediately before the MediaStore query begins.

When permission is missing, it offers actions to:

- open the app's Android settings page
- request the selected permission again

A revoked permission or `SecurityException` during scanning returns to the permission-required state. Returning from Settings does not automatically restart the scan.

## Android 14+ partial visual access

Android 14 allows users to grant access only to selected photos or videos. Because this app targets Android 14+ but does not declare `READ_MEDIA_VISUAL_USER_SELECTED`, Android uses compatibility behavior for visual-media permissions.

Current caveats:

- the user may grant temporary access only to selected images or videos
- an image or video permission can appear granted while MediaStore exposes only the selected items
- the app does not distinguish full access from partial access
- the app does not provide an in-app way to review or expand the selected set
- photo or video scan counts can therefore represent only the media currently exposed by Android

Audio access is separate and is not affected by Selected Photos Access.

## Recovered file viewers

The Recovered Photos/Videos viewer checks image and video permissions independently.

- image access loads recovered images
- video access loads recovered videos
- either permission is sufficient to open the combined viewer
- collections without permission are skipped
- permission or provider failures are handled independently per collection
- the accessible image and video results are merged and sorted newest-first

The viewer matches only the exact `Pictures/Recovered` path, with or without MediaStore's trailing slash. It does not include similarly named folders.

The Recovered Audio viewer checks `READ_MEDIA_AUDIO`, safely handles permission or provider query failures, and matches only the exact `Music/Recovered` path, with or without the trailing slash.

Both viewers reload in `onResume`, including after returning from Android settings or an external file viewer. Any previous query job is cancelled before the refreshed query starts, and view destruction cancels the active load.

Both viewers still show a permission-required message instead of launching a permission request directly.

## Recovery writes

Recovery copies are inserted through MediaStore into `Pictures/Recovered` or `Music/Recovered`. On supported Android versions, creating these app-owned entries does not require a separate broad write-storage permission. Reading the source still depends on the relevant media permission and URI access remaining valid.

Recovery work is tied to the Results view lifecycle. Leaving or recreating the Results view cancels its recovery coroutine, cancellation is not reported as a failed recovery, and completion cleanup does not access a destroyed view.

## Deferred permission work

A later permission-focused release may add:

- explicit Android 14+ partial-access handling with `READ_MEDIA_VISUAL_USER_SELECTED`
- separate full, partial, and denied visual-access states
- an in-app way to review or expand selected photo/video access

These changes are intentionally deferred and are not part of version 1.2.2.
