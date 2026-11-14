---
name: ios-project-edit
description: Edit Good Company's hand-authored Xcode project (iosApp/iosApp.xcodeproj/project.pbxproj) safely - add a Swift file, resource, localized .strings variant group, entitlement, Info.plist key, privacy manifest entry or build setting for the app, ShareExtension or NextUpWidget targets, with unique IDs; plus the Kotlin/Swift bridge rules. Use before touching anything under iosApp/ or Swift-facing Kotlin in composeApp/src/iosMain.
---

# Edit the iOS project

There is no Xcode-generated project, no CocoaPods, no xcodegen: `project.pbxproj` is a text file kept by hand.
Prefer **editing an existing Swift file** over adding one (the app shortcuts went into `iOSApp.swift` so the project
didn't change).

## IDs

Every object ID is 24 hex chars of the form `A1000000000000000000` + a 4-char suffix: `F0xx` for file references,
`B0xx` for build files, and other letters for groups, phases and targets. Pick the next unused suffix:
```bash
grep -o 'A1000000000000000000[0-9A-F]\{4\}' iosApp/iosApp.xcodeproj/project.pbxproj | sort -u | tail
```
Check your new IDs don't exist (`grep -c <id>` = 0 before you add them).

## Add a Swift file to a target

Four entries, all in `iosApp/iosApp.xcodeproj/project.pbxproj`:
1. `PBXFileReference`: `<F-id> /* Foo.swift */ = {isa = PBXFileReference; lastKnownFileType = sourcecode.swift; path = Foo.swift; sourceTree = "<group>"; };`
2. `PBXBuildFile`: `<B-id> /* Foo.swift in Sources */ = {isa = PBXBuildFile; fileRef = <F-id> /* Foo.swift */; };`
3. the `<F-id>` as a child of the target's folder group in `PBXGroup`.
4. the `<B-id>` in that target's `PBXSourcesBuildPhase` `files`.

Copy the shape of `iOSApp.swift` (`A1000000000000000000F001` / `B001`). A localized `.strings` file is a
`PBXVariantGroup` with one `PBXFileReference` per language (`en`, `pt`, `es`, `fr`, `ro`) and goes in the
**Resources** phase; copy an existing one (`InfoPlist.strings`, `Localizable.strings`).

## Rules (moved from AGENTS.md)

4. **Hand-authored Xcode project.** `iosApp/iosApp.xcodeproj/project.pbxproj` is written by hand, not exported from
   Xcode, and there's no CocoaPods or xcodegen. Three targets: the app, `ShareExtension` and `NextUpWidget` (both pure
   Swift, embedded by the app's "Embed Foundation Extensions" phase). In the app target the "Compile Kotlin Framework" Run Script phase runs
   `./gradlew :composeApp:embedAndSignAppleFrameworkForXcode` *before* Compile Sources. `ENABLE_USER_SCRIPT_SANDBOXING = NO`
   is required for that. When adding a Swift file, add a `PBXFileReference`, a `PBXBuildFile`, the group child and the
   Sources entry, all with new unique 24-hex IDs. Settings (team, bundle id, version) live in
   `iosApp/Configuration/Config.xcconfig`; put a personal `TEAM_ID` in the gitignored `Signing.xcconfig`.
   The three targets share the app group `APP_GROUP` (xcconfig → each `Info.plist` `AppGroupId` + `.entitlements`);
   register it in the Apple Developer account. Extensions can't run Kotlin: they talk to the app only through the group
   (see `composeApp/src/iosMain/.../IosBridge.kt`). Their strings are `Localizable.strings` variant groups (five languages).
- Swift calls Kotlin as `InitKoinKt.doInitKoin()` (gotcha 1 in AGENTS.md).
- New permission or API → usage string in `Info.plist`, translations in `iosApp/iosApp/<lang>.lproj/InfoPlist.strings`
  and an entry in `PrivacyInfo.xcprivacy`.
- Extensions can't run Kotlin; they read and write JSON in the app group (`IosBridge.kt`).

## Verify

On a Mac (iOS simulator available):
```bash
./gradlew :composeApp:compileKotlinIosSimulatorArm64
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -destination 'generic/platform=iOS Simulator' CODE_SIGNING_ALLOWED=NO build
```
`plutil -lint iosApp/iosApp.xcodeproj/project.pbxproj` catches syntax slips quickly. Without macOS, say in the PR
that CI's "Build (iOS)" job is the check.
