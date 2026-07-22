# Permissions

Recover Deleted Photos currently targets Android 13 and later for scanning. This document records the permission behavior in version 1.1.8 before the flow is revised.

## Declared permissions

The manifest declares:

- `READ_MEDIA_IMAGES` for photos on Android 13+
- `READ_MEDIA_VIDEO` for videos on Android 13+
- `READ_MEDIA_AUDIO` for audio on Android 13+
- `READ_EXTERNAL_STORAGE` with `maxSdkVersion="32"` as a legacy declaration

The app does not currently declare `READ_MEDIA_VISUAL_USER_SELECTED` for Android 14+ Selected Photos Access.

The Home and Scan screens block scanning below Android 13, so the legacy permission is not currently used by the active scan flow.

## Separate media permissions

Photos, videos, and audio use separate Android permissions. Android can show one combined dialog for image and video access only when both permissions are requested together.

This app currently requests only the selected type:

- Photos requests `READ_MEDIA_IMAGES`
- Videos requests `READ_MEDIA_VIDEO`
- Audio requests `READ_MEDIA_AUDIO`

Photos and videos are therefore not requested together by the current app.

## Home screen flow

The Home screen requests one permission through `ActivityResultContracts.RequestPermission`.

1. The user selects Photos, Videos, or Audio.
2. The app checks the permission for that type.
3. When granted, the app opens the Scan screen.
4. Otherwise, the first attempt launches the Android permission dialog.
5. After a denial, the main action opens the app's Android settings page.

The Home screen currently uses one temporary `hasRequestedOnce` Boolean for all three media types. After one permission is denied, switching to another type can open Settings even though that different permission has never been requested. The flag resets when the Home view is destroyed and is not persisted.

The Home screen does not refresh its button label in `onResume`. Permission is checked again when the button is pressed, so the action still works after access is granted in Settings, but its label can temporarily be stale.

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

The Recovered Photos/Videos viewer currently checks only `READ_MEDIA_IMAGES`, then queries both image and video collections.

Current caveats:

- video-only permission is not sufficient to enter the combined viewer
- image permission does not guarantee that the video query is allowed
- image and video query failures are not handled independently
- the viewer shows a permission-required message but does not request access itself

The Recovered Audio viewer correctly checks `READ_MEDIA_AUDIO`, but it also only shows a permission-required message instead of requesting access directly.

## Recovery writes

Recovery copies are inserted through MediaStore into `Pictures/Recovered` or `Music/Recovered`. On supported Android versions, creating these app-owned entries does not require a separate broad write-storage permission. Reading the source still depends on the relevant media permission and URI access remaining valid.

## Pending corrections

The next permission-focused release should consider:

- replacing the global Home-screen request flag with per-permission handling
- adding explicit Android 14+ partial-access handling
- distinguishing full, partial, and denied visual access
- allowing users to manage or expand selected photo/video access
- checking image and video permissions independently in the recovered viewer
- querying only collections the app can access
- handling each recovered-viewer query failure safely
- refreshing visible permission state after returning from Settings
