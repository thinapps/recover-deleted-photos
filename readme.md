# Recover Deleted Photos

Recover Deleted Photos is an open-source Android utility for finding photos, videos, and audio files that Android still exposes through MediaStore, including supported trashed media. Users can review and sort results, recover selected files to shared media folders, and browse recovered copies inside the app.

The app is not a forensic disk-recovery tool and cannot restore overwritten, inaccessible, or permanently deleted files. It supports Android 13 and later and works locally without Internet permission, accounts, ads, analytics, tracking, or cloud processing.

## Documentation

Recover Deleted Photos follows the shared ThinApps Guidelines by default; the app-specific documentation in this repository takes precedence where it records an intentional product or technical difference.

| Document | Description |
| --- | --- |
| [Agent Instructions](agents.md) | Defines persistent repository instructions, precedence, product guardrails, commit policy, workflow rules, and review expectations for coding agents. |
| [Scanning](docs/scanning.md) | Explains MediaStore scanning, supported results, exclusions, paging, deduplication, progress, and recovery limitations. |
| [Permissions](docs/permissions.md) | Documents Android media permissions, privacy behavior, permission flows, partial visual access, recovered-file viewers, and recovery writes. |
| [Platform](docs/platform.md) | Records Android support, build tooling, repository workflow, UI resources, transient state, performance, feedback, and Android 16 behavior. |
| [Google Play](docs/google-play.md) | Records the canonical Google Play app name, category, short description, and full description. |
| [Changelog](changelog.md) | Lists the complete version history and released changes. |
| [ThinApps Guidelines](https://github.com/thinapps/android-guidelines) | Provides the shared product, Android, repository, workflow, privacy, Google Play, and release defaults used across ThinApps projects. |
