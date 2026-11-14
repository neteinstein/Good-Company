---
name: area-backup-sync
description: Rules for data leaving or entering Good Company outside the database - backup export/restore files, Google Drive backup (drive.file scope, OAuth per platform), blogs and RSS/Atom feeds, and the sideload build's self-update from the GitHub test-build release. Use before touching ExportBackupUseCase/RestoreBackupUseCase, GoogleDriveBackupStore, FeedParser/KtorFeedFetcher, GitHubReleaseUpdateSource, CheckForUpdateUseCase, AppUpdater or test-apk.yml.
---

# Area: backup, Drive, feeds and updates

When you learn something new about this area, add it here, not to AGENTS.md. Adding stored data to backups is part
of `persistence-change`.

## Before you change anything

- A backup never contains the API key or device-only settings; restoring keeps this device's switches.
- `Backup` fields always have defaults so old files still restore.
- Drive uses only `drive.file` and one file.
- Self-update depends on the release title and asset name in `.github/workflows/test-apk.yml`; change both together.

## Reference (moved from AGENTS.md)

17. **Google Drive backup.** Scope `drive.file` only (`DRIVE_FILE_SCOPE`), one file named `DRIVE_BACKUP_FILE_NAME`,
    content = `ExportBackupUseCase` (never the API key). OAuth clients live in a Google Cloud project, not in the repo
    (see `docs/GOOGLE_DRIVE.md`): Android needs none in code; iOS reads `GOOGLE_DRIVE_IOS_CLIENT_ID` (xcconfig →
    Info.plist `GoogleDriveClientId`); web reads `<meta name="google-drive-client-id">`. Empty → the row is hidden.
    Obj-C helper classes (e.g. the `ASWebAuthenticationSession` anchor) must be `class`, never `object`.
20. **Blogs & feeds.** `FeedParser` (domain) is a tiny RSS/Atom reader; `KtorFeedFetcher` fetches straight from the
    device. Feeds live on `Friend.feeds` (so backup covers them) and refresh at most every 20 h on app start. In the
    browser many feeds fail because of CORS; that's expected.
21. **Sideload self-update.** Settings → "App updates" exists only where the manifest declares `REQUEST_INSTALL_PACKAGES`
    (`PlatformCapabilities.hasSelfUpdate`, so only the sideload build). `GitHubReleaseUpdateSource` reads the rolling
    `test-build` release (title `Test build #<run>`, run number = `versionCode`, asset `good-company-sideload-test.apk`);
    `CheckForUpdateUseCase` compares it with the installed `versionCode`; `AppUpdater` (Android) downloads the APK to
    `cache/updates/` and opens the system installer through the sideload-only FileProvider. Keep the release title and
    asset name in `.github/workflows/test-apk.yml` in step with the source. Android only installs over a build signed with
    the same key (see CI_CD.md "Signing").
