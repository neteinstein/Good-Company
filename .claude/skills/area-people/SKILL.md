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
  doesn't know is skipped when read. The same holds for a new `Closeness` level (`friend.circle`; unknown reads as not set).
- Circle colours come only from `circlePalette` (`:core:ui`).
- Pickers save on choice, form sheets on close (#64): on a person's page the birthday, family and link sheets put Save
  in `SaveSheetHeader` (above the scroll) and save when dismissed if valid and changed; a relation-to-me chip or a gift
  occasion saves at once. Each such save goes through `FriendDetailViewModel.editWithUndo`/`offerUndo`, which keeps a
  `SavedEdit` (only what that save touched) for the "Saved · Undo" snackbar (`onUndoSave`). `LocalDatePickerDialog`
  picks on the day tap, with no OK.

## Reference (moved from AGENTS.md)

27. **Closeness and circles (issue #22, D1).** Two separate ideas, mind the names. **Closeness** is the enum `Closeness`
    (Inner / Close / Good friends, one per friend, `Friend.closeness`): it used to be `Circle` / `Friend.circle`, and it is
    still stored as `circle` (`@SerialName("circle")`, the `friend.circle` column, the `circle_*` string keys and the
    `*CircleDays` settings), so backups and the database didn't change. **Circles** are the free-form groups: the UI says
    "circle", the code and tables keep `FriendGroup` / `friend_group` / `group_member` / `*Group*` use cases. A circle has an
    emoji, a `CircleColor` (seven names; light/dark values only in `:core:ui` `circlePalette`, never ad-hoc colours) and
    1+ members; `meetEveryDays` is the optional "Remind me when we haven't met" (null = off, the default for new circles)
    and never touches a member's own rhythm. A circle "met" when a hangout had enough of its members
    (`Relationships.meetupQuorum`: everyone in a circle of 1–2, else half, at least two). Home is filtered by
    `AppSettings.homeCircleFilter` (in `user_preferences`; `DeleteGroupUseCase` resets it, an unknown id reads as All):
    every section shows only members, and the A–Z list all of them whatever their closeness. Choosing a circle anywhere
    (check-in sheet, Log sheet, "Log a <circle> meetup" → `LogInteractionRoute(emptyList(), "MEETUP", circleId)`) lists its
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
    Home's menu and the year in review). Only "your people" are shown (`PeopleDashboard.isYours`: a real friend, a
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
    the person's page and in `SuggestedFamilyRoute` (Home menu, plus a row on Home). Home search understands "my nieces and
    nephews", "my cousins"… through the `KinshipVocabulary` port (`KinshipWordsFromResources` in `:core:ui`, the
    `kin_search_*` strings: `|`-separated words, accents folded) and `ParseKinshipQueryUseCase`; it finds confirmed
    kinship only. Reuse that use case for any other "my nieces" picker.
