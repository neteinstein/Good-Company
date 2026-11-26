---
name: area-backup-sync
description: Rules for data leaving or entering Good Company outside the database - backup export/restore files, encrypted backups (D4, the backup password and recovery key), Google Drive backup (private appdata folder, OAuth per platform), blogs and RSS/Atom feeds, and the sideload build's self-update from the latest GitHub release. Use before touching ExportBackupUseCase/EncryptBackupUseCase/OpenBackupUseCase/RestoreBackupUseCase, CryptographyBackupCrypto, GoogleDriveBackupStore, FeedParser/KtorFeedFetcher, GitHubReleaseUpdateSource, CheckForUpdateUseCase, AppUpdater or test-apk.yml.
---

# Area: backup, Drive, feeds and updates

When you learn something new about this area, add it here, not to AGENTS.md. Adding stored data to backups is part
of `persistence-change`.

## Before you change anything

- A backup never contains the API key, the backup key or device-only settings; restoring keeps this device's switches.
- `Backup` fields always have defaults so old files still restore.
- **Every file and Drive upload is encrypted** (D4): go through `EncryptBackupUseCase`, never write
  `ExportBackupUseCase()` out as it is. Reading goes through `OpenBackupUseCase` (old plain files still restore).
- Drive: scopes `drive.appdata` + `drive.file` (`DRIVE_SCOPES`); the encrypted backup is one file in the private
  `appDataFolder`; the old visible plain file is only read, or trashed when the user agrees (D5).
- Self-update depends on the release title and asset name in `.github/workflows/test-apk.yml`; change both together.

## Reference (moved from AGENTS.md)

17. **Google Drive backup.** Scopes `DRIVE_SCOPES` (`drive.appdata` + `drive.file`), one file
    `DRIVE_ENCRYPTED_BACKUP_FILE_NAME` in the app's private folder (`CloudSpace.APP_DATA`, Drive's `appDataFolder`),
    content = `EncryptBackupUseCase` (never the API key). The old plain visible `DRIVE_BACKUP_FILE_NAME`
    (`CloudSpace.VISIBLE`) is restored when there is no private one, and offered for deletion (to the Drive bin) after
    an encrypted backup until the user deletes or keeps it (`BackupProtection.keepOldDriveFile`). OAuth clients live in a Google Cloud project, not in the repo
    (see `docs/GOOGLE_DRIVE.md`): Android needs none in code; iOS reads `GOOGLE_DRIVE_IOS_CLIENT_ID` (xcconfig →
    Info.plist `GoogleDriveClientId`); web reads `<meta name="google-drive-client-id">`. Empty → the row is hidden.
    Obj-C helper classes (e.g. the `ASWebAuthenticationSession` anchor) must be `class`, never `object`.
20. **Blogs & feeds.** `FeedParser` (domain) is a tiny RSS/Atom reader; `KtorFeedFetcher` fetches straight from the
    device. Feeds live on `Friend.feeds` (so backup covers them) and refresh at most every 20 h on app start. In the
    browser many feeds fail because of CORS; that's expected.
21. **Sideload self-update.** Settings → "App updates" exists only where the manifest declares `REQUEST_INSTALL_PACKAGES`
    (`PlatformCapabilities.hasSelfUpdate`, so only the sideload build). `GitHubReleaseUpdateSource` reads the
    latest GitHub release (`v0.1.<n>`, title `<version> (build #<run>)`, run number = `versionCode`, asset `good-company-sideload.apk`);
    `CheckForUpdateUseCase` compares it with the installed `versionCode`; `AppUpdater` (Android) downloads the APK to
    `cache/updates/` and opens the system installer through the sideload-only FileProvider. Keep the release title and
    asset name in `.github/workflows/test-apk.yml` in step with the source. Android only installs over a build signed with
    the same key (see CI_CD.md "Signing").

## Encrypted backups (D4, issue #165)

- **Library:** `dev.whyoleg.cryptography` (cryptography-kotlin) over each platform's own crypto: JDK (Android),
  CommonCrypto (`cryptography-provider-apple`, iOS), WebCrypto (web), picked by `backupCryptoProvider()` (expect/actual
  in `core/data/.../crypto/`). Not the "optimal" provider: it pulls in CryptoKit (Swift linking, hard-coded Xcode path)
  and a prebuilt OpenSSL.
- **Format** (`CryptographyBackupCrypto`): JSON `{"format":"good-company-encrypted-backup","version":1, slots, data, mac}`.
  A random 256-bit data key encrypts the document; each slot wraps it with a key from PBKDF2-HMAC-SHA256 (password:
  600 000 rounds; recovery key: 10 000, it has 160 random bits) and a 16-byte salt. Every encryption is AES-256-CBC
  (random IV first) + HMAC-SHA256 over a label and the IV+ciphertext (encrypt-then-MAC), with separate AES/HMAC keys
  made from one secret by HMAC. No GCM: CommonCrypto has none. Raising the rounds later needs no format change
  (they're per slot); a file asking for more than 10 000 000 is refused.
- **Key on device** (owner, 2026-11-22): `BackupProtection` (the `BackupKey` + `keepOldDriveFile`) lives in key-value
  storage under `backup_key.v1` (`KeyValueBackupKeyRepository`), outside the database, so it's never in a backup and
  Android's cloud backup skips it (all shared preferences are excluded). Changing the password re-wraps the same data
  key and needs the current password or the recovery key; older files keep opening with their old password.
- **Recovery key:** 32 Crockford Base32 characters in groups of four, shown once (`RecoveryKeyDialog`), never stored.
  Typing it back ignores case, spaces and dashes (O→0, I/L→1).
- **Restoring** an encrypted backup on a device without a key adopts the file's key (`AdoptBackupKeyUseCase`), so
  that device's next backups open with the same password and recovery key. A device with its own key keeps it.
- Tests: the real cipher in `core/data` `CryptographyBackupCryptoTest` (few rounds); everything else uses
  `FakeBackupCrypto` from `:core:testing`.
