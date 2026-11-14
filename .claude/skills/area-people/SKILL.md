---
name: area-people
description: Rules for how Good Company models people and their relationships - closeness (Inner/Close/Good, still stored as "circle") vs. circles (FriendGroup), person links and relation to me, the built-in Family circle, kids as people, automatic kinship proposals and kinship search, and the network map / "Your people" dashboard. Use before touching Closeness, FriendGroup, PersonLink, LinkType, KidsAsPeople, Kinship, Network or PeopleDashboard code or screens.
---

# Area: people, circles and family

When you learn something new about this area, add it here, not to AGENTS.md.

## Before you change anything

- Mind the names: **closeness** (`Closeness`, stored as `circle`) is not a **circle** (`FriendGroup` in code and tables).
  Never rename stored names (`@SerialName`, columns, string keys): backups and the database depend on them.
- A link is stored once; read the other side with `LinkType.inverse` / `PersonLink.roleSeenBy`. Words come from
  pronouns (`linkWord`, `:core:ui`); types stay gender-neutral.
- Kinship is proposed, never set: only a tap (`ConfirmKinshipUseCase`) writes `relationToMe`.
- Anything that picks "my nieces…" reuses `ParseKinshipQueryUseCase`.
- A new `LinkType` needs no migration: `person_link.type` has no `CHECK` since `16.sqm` (issue #72), and a type this build
  doesn't know is skipped when read. The same holds for a new `Closeness` level (`friend.circle`; an unknown level reads through `closenessFrom`: Close for a real friend, else Acquaintances).
- Circle colours come only from `circlePalette` (`:core:ui`).
- Membership from a person's page (issue #70) goes through `SetCircleMemberUseCase` (returns the circle as it was;
  Undo = `RestoreGroupUseCase`). Taking out the last member keeps the circle empty, never deletes it. The "+ Circle"
  sheet lists `FriendDetail.allCircles` (stored circles only, never Family). `CircleEditorRoute(withFriend = id)`
  opens a new circle with that person in.
- The Circles tab (issue #67) is `HangoutsScreen`/`HangoutsViewModel` behind `HangoutsRoute` (route names kept): circle
  cards last met first (never met last), Family among them; a tap opens the circle's page (since #88 there is no
  "Last time with…" or memories list on the tab). `CheckInSheetHost` (Today's "I'm with…") shares that ViewModel.
  "Who's free for…?" is `WhosFreeRoute` (`:feature:hangouts`, `free/`).
- **A circle's page (issue #88)** is `CirclePageRoute(id)` → `CirclePageScreen`/`CirclePageViewModel`
  (`:feature:hangouts`, `page/`), built by `ObserveCirclePageUseCase` / `CirclePages` (domain, pure): status, "Coming up"
  (the circle's `PLANNED` plans and its reminders not done, undated last) and the timeline (meetups that reach
  `meetupQuorum` with **today's** members, everyone who came listed members first; shares saved to the circle, one
  logged as a meetup shown on it once). Family has a page too (no Edit, no delete). It emits null when the circle is
  gone and the page closes. Delete from the page pops back to the tab and hands the id over (`DELETE_CIRCLE` on the
  tab's `savedStateHandle`), so the tab's snackbar offers Undo. Circle chips on a person's page open it. Tapping a
  meetup does nothing (there is no meetup screen); a long press deletes it with Undo.
- **Circle reminders (#88).** `Reminder.circleId` (column `reminder.circle_id`, migration `20.sqm`, no foreign key)
  makes a reminder belong to a circle: `Reminder.peopleIds(circles)` is its own people plus the circle's **current**
  members (so someone who joins later sees it), used by the agenda, a member's page and notifications. A deleted
  circle's reminder keeps only its own people. `CircleReminderEditor` (`:core:ui`) is the editor with the circle chip.
- **Circle covers (#88).** `circle_cover(circle_id, image_base64)` (migration `20.sqm`, `Backup.circleCovers`), a TEXT
  base64 JPEG like `my_photo` (≤ 1080 px long side, ≤ 200 KB: `ContactPhoto.cover`, `createCoverShrinker()`), cropped
  wide with `PhotoCropDialog(shape = CropShape.WIDE)`. `FriendGroup.cover` is `@Transient`, joined in by
  `GroupsWithFamilyCircle` from `CircleCoverRepository` (so Family can have one); writes go through
  `CircleCoverUseCases`. `CircleBackdrop` (`:core:ui`) draws the cover under a veil in the circle's accent. Deleting a
  circle takes its cover and Undo (`RestoreGroupUseCase`) puts it back. The sample surf circle
  (`SampleData.COVER_CIRCLE_ID`) gets `files/sample_circle_cover.jpg` (`SampleCoverFromResources`, an original
  drawing, not a photo); removing the sample removes sample covers only.
- The People tab (`FriendsViewModel`, issues #66, #86 and #89, F5): header → search field → "Your people" → one row of
  circle chips (`PeopleFilterRow`: `AllChip` "All · N ▾", which opens the closeness menu, + `CircleChip`s, same size) → the list grouped by
  `Closeness.entries`, each heading with its count ("Close · 9"). No closeness filter chips, no folding, no circle card
  ("We met" lives on the Circles tab). A circle filters the list and keeps the grouping; "All · N" counts the list with
  no circle. Search hides the card and chips and keeps the grouping. A–Z (⋮ menu) names each row's level in a pill. A
  new level gets its heading by being added to the enum in order; give it a label in `closenessLabel`. The "Your
  people" card uses `ObservePeopleDashboardUseCase(withTrend = false)`.
- **One people picker (issue #89).** Everywhere people are chosen uses `PeoplePickerSheet` (multiple: reminder,
  plan, Log sheet, We met, circle editor, gift list) or `PersonPickerSheet` (single: person links), both in `:core:ui`
  `components/PeoplePicker.kt`; Share to… embeds its body (`PeopleFilterRow` + `peopleByCloseness`) under its
  suggestions. "I'm with…" keeps its own chips. The filtering is pure domain code: `PeopleFilter` (a circle **or** a
  closeness level, one at a time: `withCircle` resets the level, `withCloseness` clears the circle) and
  `PeopleFiltering.apply` (name search, kinship, `exclude`, `listedOnly` for the People tab's F9 rule, level counts).
  The People tab's row is the same `PeopleFilterRow`: "All · N ▾" opens Everyone + the four levels with counts. The
  circle stays remembered (`homeCircleFilter`); the level is **not** stored (in memory only, so no persistence change).
  Callers that pick people pass every circle, Family included (`FriendDetail.circles`, `ShareTargets.filterCircles`).
  Reminder and plan editors show the chosen people with `ChosenPeople` (avatar chips with × and "+ People").
- Pickers save on choice, form sheets on close (#64): on a person's page the birthday, family and link sheets put Save
  in `SaveSheetHeader` (above the scroll) and save when dismissed if valid and changed; a relation-to-me chip or a gift
  occasion saves at once. Each such save goes through `FriendDetailViewModel.editWithUndo`/`offerUndo`, which keeps a
  `SavedEdit` (only what that save touched) for the "Saved · Undo" snackbar (`onUndoSave`). `LocalDatePickerDialog`
  picks on the day tap, with no OK.

- **A person's page (issue #90).** View mode vs. edit mode (`editing`, screen state in `FriendDetailContent`, nothing
  stored; Back leaves edit mode first via `NavigationBackHandler`) and three tabs (`PersonTab`, always opens on Summary).
  Which card lives on which tab, and which show, is pure code in `detail/PersonPage.kt` (`PersonCard`,
  `ProfileSections.shown/missing`, tested in `PersonPageTest`): add a card there, not only in the composable, or the
  "+N"/"Complete…" scrolling lands on the wrong item. The family around the photo is `FamilyAround.of(friend, links)`
  (domain; it uses `LinkedPerson.role`, so the inverse is already applied) drawn by `FamilyRing` (`:core:ui`): parents
  and step-parents above, current romances beside (no exes), kids and step-kids below the name, at most four then "+N";
  siblings stay in the Family card. Circle chips have no ×: leaving a circle is an untick in `CircleSheet`. Message is
  one sheet (`MessageOptions`, preferred first); the preferred channel and "Related to you?" are set only in edit mode.
- **Everyone has a closeness; no hearts (issue #86).** `Friend.closeness` is non-null, default `ACQUAINTANCE`: every
  way in (contact sync, picker, list, .vcf, Add friend, share-to-new-friend) starts there. "Real friend"
  (`Friend.isCloseFriend`) is derived: `closeness.isRealFriend` (every level but Acquaintances). The `is_close_friend`
  column and the backup's `isCloseFriend` stay and are written from the level, so older builds read the same people;
  `FriendSerializer` (a `JsonTransformingSerializer` on `Friend`) writes both and reads old JSON with `closenessFrom`,
  the same rule as migration `19.sqm`: no level + heart → Close, no heart → Acquaintances, heart + level → unchanged.
  There is no heart anywhere (rows, a person's page, the "Real friend" toggle) and no "Not set": the level sits under
  the name on a person's page and opens `ClosenessSheet` (four levels with their rhythm, saved on choice).
  `SetClosenessUseCase` takes a non-null level; `SetRealFriendUseCase` and `shownCloseness` are gone.
  `ContactMerger.hasAppData` counts "a level above Acquaintances", never "has a level". `PeopleDashboard.isYours` is a
  real friend, a relation to you or a circle member (an Acquaintance alone isn't yours).
- **Acquaintances (issue #69).** No rhythm unless Settings sets `AppSettings.acquaintanceDays` (a `DeviceSettings`
  field, exported in backups like `keptTagSpellings`, so no schema change); `CadenceRules.daysFor` is nullable for that
  reason. They keep birthday reminders (F10). Today's last-seen card asks about an Acquaintance only for a rhythm set on
  their page (`meetEveryDays`), never for the Settings default; since #86 the people who had no level are Acquaintances,
  so this keeps asking whoever was asked before. Labels: Inner · Close · Friends · Acquaintances; the `circle_*` string
  keys stay.

## Reference (moved from AGENTS.md)

27. **Closeness and circles (issue #22, D1).** Two separate ideas, mind the names. **Closeness** is the enum `Closeness`
    (Inner / Close / Friends / Acquaintances, one per friend, `Friend.closeness`; the third is still stored as `GOOD`): it used to be `Circle` / `Friend.circle`, and it is
    still stored as `circle` (`@SerialName("circle")`, the `friend.circle` column, the `circle_*` string keys and the
    `*CircleDays` settings), so backups and the database didn't change. **Circles** are the free-form groups: the UI says
    "circle", the code and tables keep `FriendGroup` / `friend_group` / `group_member` / `*Group*` use cases. A circle has an
    emoji, a `CircleColor` (seven names; light/dark values only in `:core:ui` `circlePalette`, never ad-hoc colours) and
    1+ members; `meetEveryDays` is the optional "Remind me when we haven't met" (null = off, the default for new circles)
    and never touches a member's own rhythm. A circle "met" when a hangout had enough of its members
    (`Relationships.meetupQuorum`: everyone in a circle of 1–2, else half, at least two). The People tab is filtered by
    `AppSettings.homeCircleFilter` (in `user_preferences`; `DeleteGroupUseCase` resets it, an unknown id reads as All):
    the list shows only members, whatever their closeness. Choosing a circle anywhere
    (check-in sheet, Log sheet, the Circles tab's "We met" → `LogInteractionRoute(emptyList(), "MEETUP", circleId)`) lists its
    members with **nobody ticked** (F20). The editor is `CircleEditorRoute(id?)` in `:feature:hangouts`; delete has Undo
    (`RestoreGroupUseCase`). Migration `5.sqm` added `friend_group.color` (existing groups get colours in turn) and
    `user_preferences.home_circle_filter`.
30. **Links between people (issue #39, F6, F18).** `PersonLink` (own `person_link` table, migration `8.sqm`, in backups) is
    stored **once**: `type` is what `to` is to `from`, and `LinkType.inverse` gives the other side (`PersonLink.roleSeenBy`).
    Types are gender-neutral; the word comes from the described person's optional `Friend.pronouns` (`linkWord` in `:core:ui`,
    neutral word when null). `Friend.relationToMe` is separate; a family one puts them in the built-in Family circle, which
    `GroupsWithFamilyCircle` (domain) adds in front of the stored circles (never stored, writes to it are ignored; its name
    comes from the `BuiltInCircleNames` port). One link per pair (`SaveLinkUseCase`); anniversaries come from a current
    romance's `startedOn` (`PersonLinks.anniversaries` → agenda + notifications). Contact write-back: `ContactMirror` builds
    each card with its links (`ContactCard.links`), so a link change rewrites both contacts and a rename the linked ones.
31. **Kids are people (issue #40, F9).** A kid is a `Friend` with a `LinkType.CHILD` link from the parent (`KidsAsPeople`:
    `kidsOf` / `parentsOf` / `parentsById`); `AddKidUseCase` creates both. `Friend.kids` and `Gift.forKidId` are legacy:
    only old backups and the old JSON store carry them, and `KidsAsPeople.convert` turns them into people (in
    `RestoreBackupUseCase`, in `replaceAllData` so `LegacyJsonImport` too, and for `SampleData`); the database does the
    same in SQL as migration `10.sqm` (id kept unless taken, then `<parent>-<kid>`; link id `kid-link-<id>`) and drops `kid`, `kid_like` and
    `gift.for_kid_id`. `Friend.showInMyList` hides someone from the A–Z list only (search, circles and the parent's page
    still show them); it defaults to `KidsAsPeople.showsByDefault(parent)` = the parent is family. Agenda and notifications
    find a kid's parent through the links (`KidBirthday(parent, kid)`); `ContactCard.kids` comes from CHILD links, so a
    kid is written once (not also as a relation).
34. **Network map and "Your people" (issue #46, I12).** Both are pure domain code in `usecase/Network.kt` and
    `usecase/PeopleDashboard.kt`; the screens live in `:feature:review` (`NetworkMapRoute`, `PeopleDashboardRoute`, from
    the "Your people" card on top of the People tab and the year in review). Only "your people" are shown (`PeopleDashboard.isYours`: a real friend, a
    closeness, a relation to you or a circle member; never orphans or address-book-only contacts). Edges are person links
    (#39) plus "introduced by", never co-presence; you are the `NetworkGraph.ME` node at the origin. The layout is our own
    deterministic Fruchterman–Reingold (`ForceLayout`, fixed iteration budget, positions cached between emissions and
    re-run for a third of the budget), drawn in a Compose `Canvas`: no graph library. Warmth is the same `Warmth.score`;
    with Show warmth off node size and every dashboard warmth figure go away and only counts remain.
35. **Automatic kinship (issue #41, F7).** `KinshipResolver` (domain, `usecase/Kinship.kt`, pure) proposes what someone
    one step from a family member is to you (`step(viaToMe, personToVia)` is the whole table: brother's daughter → niece,
    mother's sister → aunt, partner's mother → in-law). Ambiguous cases (half/step family, a parent's child or partner,
    in-laws through an ex) offer a choice and are **never set automatically**; two parents who share a child make them a
    plain sibling. A proposal only changes anything when the user taps it (`ConfirmKinshipUseCase` → `relationToMe`);
    "Not right" / "Not family" adds the offered relations to `Friend.dismissedKinship` (`friend.dismissed_kinship`,
    comma-separated `LinkType` names), which are never offered again. `STEP_PARENT` / `STEP_CHILD` / `HALF_SIBLING` /
    `STEP_SIBLING` are family `LinkType`s; migration `14.sqm` rebuilt `person_link` for the wider `CHECK` (gone since `16.sqm`). Proposals show on
    the person's page and in `SuggestedFamilyRoute` (People ⋮ menu, plus a row on People). People search understands "my nieces and
    nephews", "my cousins"… through the `KinshipVocabulary` port (`KinshipWordsFromResources` in `:core:ui`, the
    `kin_search_*` strings: `|`-separated words, accents folded) and `ParseKinshipQueryUseCase`; it finds confirmed
    kinship only. Reuse that use case for any other "my nieces" picker.
- **Last-seen card and never-logged people (issue #68, N4).** Someone with a rhythm (`CadenceRules.cadenceFor`) but
    nothing logged is **not overdue**: `Relationships.meetupStatuses` gives them no status at all. Today's "When did you
    last see…?" card (`LastSeenQuestions.pending`, `ObserveLastSeenCardUseCase`, `:feature:agenda` `LastSeenCard`) asks
    about them one at a time, closest first; `LastSeenAnswer` stores `Friend.approxLastSeen` (`friend.approx_last_seen`,
    migration `17.sqm`, in backups): this week 3 days ago, this month 15, a few months 90, over a year 400, "I don't
    know" today (owner decisions 2026-11-14). It only moves the due date: `MeetupStatus.lastSeenDate`/`daysSinceLastSeen`
    stay null, the person's page and timeline never show it, it isn't a hangout (no warmth, no stats), and a real
    meetup (or any contact when `contactCountsAsCatchUp`) always wins. "Not now" is `AppSettings.lastSeenCardHiddenOn`,
    device-only (`DeviceSettings`, stripped from exports, kept on restore). Acquaintances (#69) must not be asked.
