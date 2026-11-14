---
name: area-sharing-gifts
description: Rules for "Share to Good Company" (Android ACTION_SEND, iOS Share Extension, web share_target), attachments for a person or a circle, gift ideas and the gift lists per occasion (to buy / bought / given, budgets, totals). Use before touching ShareInbox, ShareRoute, SaveSharedUseCase, ShareMatcher/ShareGuesser, Attachment, Gift, GiftList or the gallery photo picker/opener.
---

# Area: sharing, attachments and gifts

When you learn something new about this area, add it here, not to AGENTS.md.

## Before you change anything

- The app never fetches a shared link; titles come only from the sending app.
- Photos are a thumbnail plus an optional gallery reference, never the full photo.
- An attachment has exactly one owner: a person or a circle.
- A circle's shares show on its page (#88, `TimelineEntry.Share`); one logged as a meetup shows once, on that meetup.
  Deleting a share there goes through `DeleteTimelineEntryUseCase` (Undo restores the attachment).
- There is one gift store (the person's `Gift`s); lists only point at them. Never guess a currency.
- Share changes usually touch three platforms: Android `MainActivity`/`ShareIntents`, iOS `ShareExtension` (see
  `ios-project-edit`), web `LaunchOptions.shared`. Keep them in step.

## Reference (moved from AGENTS.md)

19. **Share into the app (issue #24, D6, C1, F12, F17).** "Share → Good Company" lands in `ShareInbox` (domain), which
    opens `ShareRoute` (who → confirm). Android: `ACTION_SEND` in `MainActivity` (`ShareIntents`: images downscaled to
    1568 px JPEG, `EXTRA_TITLE`/`EXTRA_SUBJECT` as the title). iOS: the Share Extension writes `ShareInbox/*.json`
    (+ `.jpg`, plus the item title) into the app group; the app reads it when it becomes active. Web: the manifest's
    `share_target` (installed PWA only) → `?share_title&share_text&share_url` → `LaunchOptions.shared`.
    The share is saved by `SaveSharedUseCase` as an `Attachment` (own `attachment` table, migration `6.sqm`, in backups)
    for a person **or** a circle (exactly one owner; a circle's shows on every member's page via `Attachment.isFor`), or
    as a `Gift` idea (`url`, `price`, person only). When it counts, it is logged through `LogInteractionUseCase`
    (`source = SHARE`) and `Attachment.logId` points at the hangout / interaction / batch, which is how `Timeline.build`
    shows it on that entry. Photos: a thumbnail (`PhotoShrinker`) plus an optional `PhotoRef` to the gallery item, never
    the full photo. `rememberGalleryPhotoPicker` (Android Photo Picker + persistable grant, iOS `PHPicker` asset id) and
    `rememberGalleryPhotoOpener` (`ACTION_VIEW` / `PHImageManager`; web: never) live in `:core:ui` platform; share-sheet
    photos usually get no reference (thumbnail only). Friends are ranked by `ShareMatcher` (name, phone number via
    `PhoneMatcher`, now in domain `util/`, recent contact, closeness); the type is guessed by `ShareGuesser`. Link titles
    come only from the sending app: the app never fetches a shared link.
36. **Gift lists (issue #42, F8, F17).** A `GiftList` (title, optional `occasionDate`, `occasion` = the `GiftOccasion` a
    gift given from it is recorded as) has `GiftListEntry`s (a person, an optional `budget`) whose `GiftListItem`s point at
    that person's own `Gift`s by id with a `GiftStatus` (to buy → bought → given → to buy). There is no second gift store:
    a gift added on a list is an idea on the person's page, and "given" sets `Gift.givenOn` / `occasion` there
    (`AdvanceGiftStatusUseCase`; moving on from given makes it an idea again). Tables `gift_list`, `gift_list_entry`,
    `gift_list_item` (migration `15.sqm`, in `Backup.giftLists`, in `SampleData`); a list is saved whole (delete + insert),
    and `gift_id` has no foreign key because a friend's gift rows are rewritten on every save, so items whose gift is gone
    are skipped when read. Prices are free text: `GiftPrices.parse` reads a number where it can and `GiftTotals` says when
    a sum is partial; never guess a currency. "Duplicate" keeps people and budgets a year on, with no items. Adding people
    takes a whole circle or a search that understands kinship (`ParseKinshipQueryUseCase`, #41). AI ideas reuse
    `SuggestGiftsUseCase` and show only with `usableAiKey` (gotcha 33). Screens: `GiftListsRoute` / `GiftListRoute(id)` in
    `:feature:hangouts` (Together tab row, plus "Gift lists" on a friend's gift card).
