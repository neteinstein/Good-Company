# Google Drive backup — setup

"Back up" / "Restore" under **Settings → Your data → Google Drive** store one **encrypted** file in the app's private
folder in the user's own Google Drive (`appDataFolder`, hidden from the Drive UI; decisions D4 and D5). It is the same
content as **Export backup**, encrypted with the backup password (never the AI API key). Calls go straight from the
device to Google; there is no Good Company server.

The app asks for two scopes: **`drive.appdata`** (its own private folder) and **`drive.file`** (files it created).
`drive.file` is only for the old plain, visible `Good Company backup.json` that earlier builds wrote: it can still be
restored, and after the first encrypted backup the app offers to move it to the bin. Neither scope lets the app see
anything else in the user's Drive, and Google treats both as non-sensitive, so the consent screen doesn't need
Google's security review.

Each platform needs an OAuth client in one Google Cloud project. Until a client exists, the feature can't work:
Android shows "Google Drive isn't set up for this build" when tapped; iOS and web hide the row.

## 1. Google Cloud project (once)

1. In [Google Cloud Console](https://console.cloud.google.com/), create (or pick) a project.
2. **APIs & Services → Library → Google Drive API → Enable.**
3. **APIs & Services → OAuth consent screen**: app name "Good Company", support email, the privacy policy URL,
   and add the scopes `https://www.googleapis.com/auth/drive.appdata` and `https://www.googleapis.com/auth/drive.file`. Publish the app (while it's in "Testing", only
   listed test users can sign in).

## 2. Android

**Credentials → Create credentials → OAuth client ID → Android**, once per signing key and package:

| Build | Package name | SHA-1 |
|---|---|---|
| Play (`release`) | `com.neteinstein.goodcompany` | Play App Signing key (Play Console → Setup → App signing) |
| `sideload` | `com.neteinstein.goodcompany.sideload` | your release keystore (`keytool -list -v -keystore …`) |
| `debug` | `com.neteinstein.goodcompany` | `~/.android/debug.keystore` (`keytool -list -v -alias androiddebugkey -keystore ~/.android/debug.keystore -storepass android`) |

Nothing goes into the code: Play services' `AuthorizationClient` matches the app by package name and signing
certificate. A missing client surfaces as `DEVELOPER_ERROR`, which the app shows as "not set up", in a dialog that
names the package and the SHA-1 of the certificate the running build is signed with (copy it into the client).

**Test APKs from the GitHub release (issue #166).** Without the `ANDROID_KEYSTORE_*` secrets, the Test APK workflow
signs with the CI runner's debug key, which is a new key on every run ("Signed with: CI debug key" in the release
notes). No OAuth client can match a key that changes with every build, so Drive always fails with "not set up" on
those APKs. Add the keystore secrets (CI_CD.md), then register that keystore's SHA-1 for
`com.neteinstein.goodcompany.sideload` (and for `com.neteinstein.goodcompany` if you test the Play APK).

## 3. iOS

1. **Create credentials → OAuth client ID → iOS**, bundle ID `com.neteinstein.goodcompany`.
2. Put its client ID (`1234567890-abc….apps.googleusercontent.com`) in `GOOGLE_DRIVE_IOS_CLIENT_ID`, either in
   `iosApp/Configuration/Config.xcconfig` or in the gitignored `iosApp/Configuration/Signing.xcconfig`.
   It flows into Info.plist as `GoogleDriveClientId`.

The app signs in with `ASWebAuthenticationSession` and PKCE, then exchanges the code for a token. The redirect is
the reversed client ID (`com.googleusercontent.apps.<id>:/oauth2redirect`), which `ASWebAuthenticationSession`
catches itself, so no URL type is needed in Info.plist. iOS OAuth clients have no secret.

## 4. Web

1. **Create credentials → OAuth client ID → Web application**, and add the site's origin (e.g.
   `https://goodcompany.example`, plus `http://localhost:8080` for local dev) under **Authorized JavaScript origins**.
2. Put the client ID in `composeApp/src/wasmJsMain/resources/index.html`:
   `<meta name="google-drive-client-id" content="….apps.googleusercontent.com">`.

The web build loads Google Identity Services (`accounts.google.com/gsi/client`) only when a client ID is set, and
asks for a token when the user taps the button. The token is kept only in memory.

## Automatic backup and restoring on a new phone (issue #166)

- **Android only:** `DriveAutoBackupWorker` (WorkManager, about once a day, on a network) runs `RunAutoBackupUseCase`.
  It backs up when one is due (Settings → Google Drive → Automatic backup: Daily, the default, Weekly or Off), with a
  token from `PlayServicesDriveTokenSource` (`authorize` without UI, using the access granted on "Back up now"). It
  starts only after a first "Back up now" on the device. A refused token is cleared (`clearToken`) and tried once
  more. The result (or why it couldn't run: no password, sign-in needed, not set up, …) is shown under the setting.
- **Intro guide → Restore a backup:** from Google Drive or a file, with the backup password or the recovery key.

## How it works (for contributors)

- `core/domain/.../usecase/DriveBackupUseCases.kt`: `BackupToDriveUseCase`, `FetchDriveBackupUseCase`, the file
  name and the scope. Restore reuses `RestoreBackupUseCase` behind the same "Replace everything?" confirmation.
- `core/data/.../cloud/GoogleDriveBackupStore.kt`: Drive v3 over Ktor. Lookup by name, multipart create, media
  update of the same file, and download. HTTP errors map to `CloudBackupFailure.Reason`.
- `core/ui/.../platform/`: `rememberGoogleDriveAuthorizer()` per platform, plus `PlatformCapabilities.hasGoogleDrive`.
- Tests: `GoogleDriveBackupStoreTest` (Ktor `MockEngine`), `DriveBackupUseCasesTest`, `SettingsViewModelTest`
  (`FakeCloudBackupStore` in `:core:testing`). The real sign-in flows need a configured project and a device.
