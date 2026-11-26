---
name: area-contacts
description: Rules for Good Company's address-book code - choosing contacts (system picker vs. the checkbox list), contact sync, contact photos, .vcf import and the Android sideload write-back that mirrors friends onto contacts (never deletes, never edits the user's rows, never writes emails, postal addresses or photos). Use before touching ContactsSource/ContactsWriter, AndroidContactsWriter, ContactCard, ContactRowPlanner, ContactMerger, ChooseContactsRoute, photos, or contact permissions.
---

# Area: contacts

Store policy comes first (AGENTS.md gotcha 8): contact and call-log permissions exist only in the sideload manifest;
the Play build is picker-only. When you learn something new about this area, add it here, not to AGENTS.md.

## Before you change anything

- New data that fits on a contact → `ContactCard` (domain, with tests) **and** `AndroidContactsWriter.rowsFor`, labels in
  `core/data/src/androidMain/res/values*/strings.xml` (five languages). iOS and web use `ContactsWriter.None`.
- The write-back only adds or updates the app's own rows (`ContactRowPlanner.Step` has no delete). Keep it that way.
- Never import the whole address book: people are chosen (list with checkboxes or system picker); sync refreshes only
  friends with a `contactId`.
- Photos: thumbnails only, through `PhotoShrinker`; contact-owned like the birthday (`ContactMerger`).
- Notes (issue #103): `ContactCard.notes` still comes from the legacy `Friend.notes` text, which the app no longer
  edits (notes are dated journal entries now); new dated notes are not written to the contact.
- Picking **one** person (list or system picker) opens their page; several keep the "Added N" snackbar
  (`PickedContactsResult.picked` / `single`, issue #114). Onboarding ignores it.
- Tests: `ContactRowPlannerTest`, `ContactWriteBackTest` (`core/data/src/commonTest`), plus domain tests for `ContactCard`.

## Reference (moved from AGENTS.md)

22. **Contact write-back (Android sideload only).** `FriendRepositoryImpl` mirrors every friend that has a `contactId` onto
    their contact through the `ContactsWriter` port (`ContactCard.from(friend)` is the projection; a write happens only when
    the card changed, and `syncContacts` backfills friends with details). `AndroidContactsWriter` keeps it all in one raw
    contact the app owns (`SOURCE_ID` = `goodcompany:<friendId>`, no account, so it never syncs to Google) joined to the real
    contact with an aggregation exception. Never edit the user's own contact rows. The mirror
    **never deletes** (owner decision): one row per item (one note, one birthday, one row per kid (from CHILD links)/event), a changed detail
    updates the app's own earlier row in place (`ContactRowPlanner`, pure and tested; its `Step` has no delete), and a detail
    removed in the app, a cleared friend or a deleted friend leaves the contact as it is. The user's own contact rows are only read.
    `AndroidContactsSource` skips contacts made only of our raw contacts and has `importsNewContacts = false` (so does
    `IosContactsSource` since D2): people are chosen in the contact list with checkboxes (`ListContactsUseCase` reads without
    photos, `SaveChosenContactsUseCase` reads `ContactsSource.readPhotos` for the ticked people only, then
    `ImportPickedContactsUseCase`) or with the system contact picker, and a sync only refreshes friends that already have a
    `contactId`, never importing the whole address book. `ContactRecord.starred` is Android's `STARRED` (iOS has none). New data that fits on a contact goes into
    `ContactCard` (domain, tested) and `AndroidContactsWriter.rowsFor`; its labels are Android string resources in
    `core/data/src/androidMain/res/values-*` (all five languages). Other platforms use `ContactsWriter.None`.
    What is written (issue #20): relatives as `Relation` rows (partner → `TYPE_PARTNER`, parent → `TYPE_PARENT`; sibling, pet
    and other → `TYPE_CUSTOM` with a localized label, since Android has no sibling type), "introduced by" as `TYPE_REFERRED_BY`,
    and job, lives in, how we met, food, drinks, interests and custom fields as labelled lines in the one note (`NoteLabels`).
    **Emails are never written back** (owner decision); they are read from the contact (`ContactRecord.emails`, `fromContact`)
    and refreshed on sync, while typed ones are kept.
25. **Contact photos (issue #15).** `Friend.photo` is a `ContactPhoto` (base64 thumbnail, at most `ContactPhoto.MAX_BYTES`),
    stored in its own `friend_photo` table and carried in backups. It is contact-owned like the birthday: a sync or a re-pick
    replaces it, a contact without one never clears it (`ContactMerger`), and `.vcf` imports only fill a missing one.
    Sources: Android `AndroidContactsSource` (the `Photo.PHOTO` thumbnail of `Contacts.PHOTO_ID`) and both Android pickers
    (best effort through the picker's grant; the Android 17 picker isn't asked for the photo field, so it never turns into
    a field chooser); iOS `CNContactThumbnailImageDataKey` (check `isKeyAvailable` first: an unfetched key is an Obj-C
    exception); `VCardParser` reads inline `PHOTO`, never links. Larger pictures go through the `PhotoShrinker` port
    (`createPhotoShrinker()` in `:core:ui`: Android graphics, Skia on iOS/web) to `MAX_SIDE_PX`. Render with
    `FriendAvatar(friend)` / `StackedAvatars(friends)`. Widgets show the catch-up friend's photo (`WidgetSnapshot.CatchUp.photo`
    on iOS) only when not private. The contact write-back never writes photos. A friend without a photo can get one from the
    gallery (`rememberImagePicker` → `SetFriendPhotoUseCase`): each picker returns an upright JPEG of at most 512 px (UIKit
    redraw on iOS, EXIF rotation on Android, `createImageBitmap` on the web), since the Skia decoder ignores EXIF.

## A person's background image (issue #144)

App-owned like `customPhoto`: `person_background` is its own table, `ContactCard` knows nothing of it, a sync never
touches it and it is never written back. Details in `area-people`.

## Your own photo (issue #71, N7)

Not a contact photo, but it follows the same rules: a `ContactPhoto` thumbnail (`SetMyPhotoUseCase` shrinks through
`PhotoShrinker`, `MAX_BYTES`), picked with `rememberImagePicker` and cropped with `PhotoCropDialog` (Settings → first
card, Change / Remove with Undo). Stored in its own one-row `my_photo` table (migration `18.sqm`), carried as
`Backup.myPhoto` (null in older backups, which clears it on restore). It is never written to contacts (`ContactCard`
knows nothing of it) and never sent to the AI. `AppViewModel` observes it and `App` provides it as `LocalMyPhoto`
(`:core:ui` `HeaderButtons.kt`); readers are `HeaderSettingsButton` (photo instead of the gear, every tab header), the
network map's centre node and the year in review hero card. Add a new place by reading `LocalMyPhoto`, not by
injecting the use case into another ViewModel.

## Contact button, socials and websites (issue #118)

- One contact button on the person page (`MessageOptions.contact`): Call, WhatsApp, Text, Email, preferred first; the
  icon is the phone only when Call is preferred (`MessageOptions.showsPhone`).
- `Friend.socials` (`SocialLink`, `SocialNetwork`, `Socials.parse`/`url` in `model/Socials.kt`) and `Friend.websites`
  are app-owned, in table `friend_link` (`kind` = network name or `WEBSITE`, migration `25.sqm`). Never written back
  to contacts, never fetched. Logos: `SocialIcons` in `:core:ui` (Simple Icons, CC0, `docs/licenses/simple-icons.md`);
  never redraw them.

## Postal addresses (issue #169)

- `Friend.addresses`: at most one `AddressKind.HOME` and one `WORK` (`PostalAddress(kind, text, fromContact)`), free
  text in table `friend_address` (migration `35.sqm`, `PRIMARY KEY (friend_id, kind)`, kind without `CHECK`: an
  unknown one is skipped when read). Carried in backups through `Friend`.
- Read from Contacts like emails: `ContactRecord.addresses` ← Android `StructuredPostal` `TYPE_HOME`/`TYPE_WORK`
  (`FORMATTED_ADDRESS`; sideload sync and the legacy picker's best-effort data rows, `DATA1` + `DATA2`), iOS
  `CNContactPostalAddressesKey` with `CNLabelHome`/`CNLabelWork` through `CNPostalAddressFormatter` (source and picker;
  check `isKeyAvailable`). The Android 17 picker is still asked for the name only. `.vcf`: `ADR;TYPE=HOME|WORK`
  (`LABEL` wins, else street / "postcode locality" / region / country lines). Other labels are never read.
- Merge per kind (`ContactMerger.mergeAddresses`): typed wins, else the contact's, else none. Re-picks and `.vcf` only
  fill a missing kind (`fillAddresses`); `.vcf` ones count as typed. Typed ones are app data (`hasAppData`).
- Never written back (`ContactCard` has no address), never in `FriendProfile` (AI). No validation, autocomplete or
  geocoding. "Open in maps" is `mapsLink` (`:core:ui` platform: `geo:` / Apple Maps / a Google Maps search URL in a
  new tab) handed to `LocalUriHandler`; the app calls no map service (gotcha 29).
