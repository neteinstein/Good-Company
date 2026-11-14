---
name: refresh-branch
description: Bring main (or the parent branch of a stack) into a Good Company feature branch and fix what parallel work breaks - colliding .sqm migration numbers and schema snapshots, AGENTS.md gotcha numbers, Koin/navigation/TestGraph/strings conflicts, import order - then re-verify. Use when a PR is behind or conflicts, after a lower PR in a build-in-sequence stack merged or changed, or when asked to "update/rebase/merge main into" a branch.
---

# Refresh a branch

The repo merges PRs with **merge commits**, and pushed branches are never force-pushed. So refresh by **merging**,
not rebasing: `git fetch origin && git merge origin/main` (in a stack: `git merge origin/<parent branch>`).
Work in the branch's own worktree with a clean tree.

## 1. Conflicts you will meet

| File | How to resolve |
|---|---|
| `composeApp/.../di/AppModule.kt`, `AppModuleTest.kt` | keep both sides' registrations and assertions; imports sorted |
| `composeApp/.../ui/navigation/AppNavHost.kt`, `Routes.kt` | keep both routes and `composable<…>` blocks |
| `core/testing/.../TestGraph.kt`, `Fakes.kt` | keep both fakes and use cases; the constructor gets both new parameters |
| `**/composeResources/values*/strings.xml` | keep both sides' strings, in all five languages; no duplicate `name` |
| `core/domain/.../model/Backup.kt`, `SqlRepositories.kt` (`replaceAllData`, export) | keep both fields and both blocks |
| `docs/PRODUCT_BRIEF.md` Decisions | keep both rows, in date order |
| `AGENTS.md` / `.claude/skills/area-*` | keep both sections; see §3 for numbering |

Never resolve by dropping the other side's code: it is merged work.

## 2. Two migrations with the same number

Both branches added `DB/<n>.sqm` and `sqldelight/databases/<n+1>.db` (`DB` = `core/data/src/commonMain/sqldelight/com/neteinstein/goodcompany/core/data/db/`).
The base's version wins its number; yours moves up:

1. Save yours first (`git show HEAD:<DB>/<n>.sqm > /tmp/ours.sqm`), then take the base's files:
   `git checkout origin/main -- <DB>/<n>.sqm core/data/src/commonMain/sqldelight/databases/<n+1>.db`
   (in a stack, the parent branch instead of `origin/main`).
2. Put your saved migration back as `<n+1>.sqm` and fix its first line
   (`-- Version <n+1> → <n+2> …`). If the base went further than one, use the next free number.
3. Re-apply your `.sq` edits on top of the base's (the `.sq` files must equal the end of all migrations).
4. `./gradlew :core:data:generateCommonMainGoodCompanyDatabaseSchema :core:data:verifySqlDelightMigration` and commit the
   new top `.db`. Delete nothing else under `databases/`.
5. Fix every mention of the old number: `MigrationTest` versions, comments, the area skill or AGENTS.md text
   ("migration `13.sqm`"), the PR description.

Commit as `fix(data): renumber <thing> migration to <n+1> after merging main`.

## 3. AGENTS.md and skills

Gotchas are numbered; if both sides added number N, yours becomes the next free number. Fix references to it
("gotcha N"). Area-specific notes belong in `.claude/skills/area-*`, not AGENTS.md.

## 4. Re-verify (always, even with no conflicts)

```bash
./gradlew spotlessApply      # merged imports are often out of order
./gradlew testDebugUnitTest koverVerify spotlessCheck :androidApp:lintDebug :androidApp:assembleDebug
./gradlew :composeApp:compileKotlinIosSimulatorArm64 :composeApp:compileKotlinWasmJs
```

A green merge can still break: two features may each be fine and wrong together (same string name, a `TestGraph`
parameter order, a use case one side renamed). Read the merged diff of the hot files above.

## 5. Push and the rest of the stack

`git push` (no force). In a `build-in-sequence` stack, every branch above this one now has to merge it in turn, bottom
up, with this same skill.
