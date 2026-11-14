---
name: persistence-change
description: Checklist for any change to what Good Company stores - a new SQLite table, a new column, a changed CHECK, a new field in backups or device settings. Covers the .sq/.sqm files, schema snapshots, mappers and *_TABLES observers, SQL repositories, backup export/restore, migration and contract tests, fakes and Koin. Use before touching core/data/src/commonMain/sqldelight, db/*Tables.kt, SqlRepositories.kt, Backup.kt or AppSettings.
---

# Persistence change

Read the rules at the bottom first (moved here from AGENTS.md). A complete worked example is gift lists (issue #42):
`git show --stat $(git log --format=%h -1 --grep="gift lists per occasion" --no-merges)`.

Paths below are relative to `core/data/src/commonMain/` unless they say otherwise.
`DB` = `sqldelight/com/neteinstein/goodcompany/core/data/db/`.

## 1. Which kind of change?

| Change | Where |
|---|---|
| Data that must be readable before the database opens (theme, app lock, onboarding, API key) | `DeviceSettings` (JSON `device_settings.v1`): no migration |
| A user preference | a column on `user_preferences` (`DB/UserPreferences.sq`) + `AppSettings` |
| A new field on an existing thing | a column (nullable or with a default) |
| A new thing / a list hanging off one | a new table |
| A new value in an enum column (closeness, link type, status…) | **no migration**: enum columns have no `CHECK` since `16.sqm` (issue #72). Give the mapper's fallback a thought |
| Any other `CHECK` change, or dropping a constraint | rebuild the table in a migration (see below) |

## 2. Schema

1. **Find the version.** The highest `DB/<n>.sqm` migrates `n → n+1`; the highest `sqldelight/databases/<m>.db` is the
   current schema (`m = n + 1`). Your migration is `DB/<m>.sqm` and produces `<m+1>.db`.
2. **`.sq` file**: the `CREATE TABLE` / `CREATE INDEX` as the table looks *now*, plus every query. For a new column,
   edit the `CREATE` here too: `verifyMigrations` checks that the migrations end where the `.sq` files are.
3. **`DB/<m>.sqm`**: start with `-- Version <m> → <m+1> (issue #<n>, F<x>): <what and why>.` Then `CREATE TABLE …` or
   `ALTER TABLE … ADD COLUMN …`. Backfill existing rows in the same file when they need a value.
4. Generate and commit the snapshot, then verify:
   ```bash
   ./gradlew :core:data:generateCommonMainGoodCompanyDatabaseSchema   # writes sqldelight/databases/<m+1>.db
   ./gradlew :core:data:verifySqlDelightMigration
   ```
   `git add` the new `.db` (binary). Never edit or regenerate older snapshots.

## 3. Code, in this order

1. **Domain** (`core/domain/.../model/`): the model, and its field on `Backup` (`model/Backup.kt`) **with a default**,
   so older backup files still read. A repository port in `core/domain/.../repository/Repositories.kt`.
2. **Mapper** in `kotlin/.../core/data/db/*Tables.kt` (own file for a family of tables, e.g. `GiftListTables.kt`):
   read/write functions plus a `*_TABLES` array with **every table the reads touch** (joined ones too, often `"friend"`).
   Observers re-emit only for the tables listed.
3. **Repository** in `repository/SqlRepositories.kt`: go through `db.access` / `db.transaction` / `db.observe(…_TABLES)`.
   A save that rewrites children is one `transaction` (delete + insert). New tables need no JSON `*RepositoryImpl`;
   those exist only for the old stores.
4. **Backup**: add the data to `SqlBackupRepository` export and to `GoodCompanyDatabase.replaceAllData` (same file), in
   foreign-key order. If the domain has a conversion for old data (e.g. `KidsAsPeople.convert`), call it there.
   Device-only settings are stripped from exports and kept on restore.
5. **Koin**: register the repository and use cases in `composeApp/.../di/AppModule.kt`.
6. **Fakes**: a fake repository in `core/testing/.../Fakes.kt`, wired into `TestGraph.kt` with its use cases.
7. **Sample data**: if the demo should show it, extend `SampleData` (and `SampleTextsFromResources` for its words).

## 4. Tests (all required)

- `core/data/src/androidHostTest/.../db/MigrationTest.kt`: open a database at version `<m>` with real rows, migrate,
  assert the rows and the new defaults/backfill.
- `core/data/src/androidHostTest/.../SqlRepositoryContractTest.kt` (and `commonTest/.../RepositoryContract.kt` when the
  contract is shared): save, read, observe, delete.
- A backup round trip (`commonTest/.../BackupAndPlansTest.kt`): export → restore → equal.
- Domain tests for the use cases (`core/domain/src/commonTest`).
- `./gradlew testDebugUnitTest koverVerify`.

## 5. Web and gotchas

- Flags are `INTEGER` 0/1, converted in the mapper (the web driver can't read SQLite booleans).
- A `CHECK` naming a column added by `ALTER TABLE … ADD COLUMN` breaks SQLDelight's parser: validate in the mapper.
- **Enums are `TEXT` with no `CHECK`** (issue #72). Write the name; read with `enumOrNull<E>(…)` (`db/Columns.kt`), never
  `valueOf`, and put the fallback for an unknown name next to the call (e.g. closeness → not set, relation → other,
  interaction type → row skipped but kept). A newer build or backup can always store a value this build doesn't know.
  `0/1` flags and ranges keep their `CHECK`s. History: `3.sqm`, `4.sqm` and `14.sqm` rebuilt tables just to widen an
  enum `CHECK`; `16.sqm` removed them all.
- **Rebuilding a table runs with foreign keys on**: Android, iOS and the web worker all migrate inside a transaction,
  where `PRAGMA foreign_keys = OFF` is a silent no-op. So `DROP TABLE friend` (or any parent) cascades into every child
  table (`ON DELETE SET NULL` too), and SQLite refuses the drop ("no such table") while a child of it also references
  another table that is already dropped. `16.sqm` is the pattern: copy every table that could lose a row into a
  constraint-free `keep_*` table (`CREATE TABLE … AS SELECT *`), drop children first, recreate, refill parents first
  (`DELETE` then insert for tables that weren't dropped), drop the copies. SQLDelight doesn't know `rowid` on the copies.
  Test it with child rows in every table and foreign keys on (`EnumColumnsMigrationTest`).
- A column called `value` is generated as `value_`.
- No foreign key to rows that a parent's save rewrites (e.g. `gift_list_item.gift_id`): skip dangling rows when reading.
- Another branch may take the same migration number before you merge: see the `refresh-branch` skill.
- If the gotchas in AGENTS.md mention the change (e.g. "migration `15.sqm`"), update the matching area skill.

## Rules (moved from AGENTS.md)

9. **Persisted data.** App data lives in SQLite (gotcha 24); the `.sq` files are the schema. Adding a nullable column
   or one with a default is a new numbered `.sqm` migration. A new table also needs its read/write in `db/*Tables.kt`,
   its name in the matching `*_TABLES` list (observers), and a place in `SqlBackupRepository` / `replaceAllData` and the
   domain `Backup` (export / restore). SQLDelight can't parse a `CHECK` on a column added with `ALTER TABLE … ADD COLUMN`
   when it names the new column, so check such values in the mapper (e.g. `preferred_channel`). A column called `value` is
   generated as `value_`. Device settings that must be readable before the database opens (theme, app lock,
   onboarding, API key) are `DeviceSettings`, JSON under `device_settings.v1`; the rest of `AppSettings` is the
   `user_preferences` row. The old `*.v1` JSON keys in `storage/PersistentState.kt` (`Keys`) are only read by the
   one-off `LegacyJsonImport`.
24. **SQLite (SQLDelight 2, issue #14).** `:core:data` has `GoodCompanyDatabase`
    (`src/commonMain/sqldelight/`, `generateAsync = true`, so every query is `suspend`), opened lazily through
    `AppDatabase` from the `createDatabaseDriverFactory()` expect/actual. The repositories are
    `repository/SqlRepositories.kt` (mappers in `db/*Tables.kt`). On first open `LegacyJsonImport` moves the old JSON
    documents in, in one transaction with a `db_meta` mark, then deletes the JSON (owner decision 2026-11-13), but only
    when the database is persistent; unreadable keys and a failed import keep the JSON. The JSON `*RepositoryImpl`
    classes remain only as the reference the SQL ones are checked against: both must pass `RepositoryContract`
    (commonTest; the SQL subclass is in androidHostTest).
    Go through `AppDatabase.access`/`transaction`/`observe`, never `get()` directly: the web driver can't run two
    statements at once. Schema changes go in numbered `.sqm` migrations; after one, run
    `generate<SourceSet>GoodCompanyDatabaseSchema` and commit the new `databases/<n>.db` (`verifySqlDelightMigration`
    checks them). iOS: `ComposeApp.framework` is static, so the app target links `-lsqlite3` itself (`OTHER_LDFLAGS`).
    Web: our own worker (`core/data/src/wasmJsMain/sqlite-worker`, official `@sqlite.org/sqlite-wasm`, OPFS
    "SAH pool" VFS, no COOP/COEP needed) replaces SQLDelight's in-memory one. It's an npm `file:` package, so the
    build uses a copy taken at install time: after editing it, bump `version` in its `package.json` (or rerun
    `kotlinNpmInstall`), or the bundle keeps the old worker; the database works in one tab at a time,
    and a second tab falls back to memory (`AppDatabase.isPersistent()` is false; the app shows a "won’t be saved" banner). `index.html?dbcheck` prints a
    persistence smoke test to the console (open count must go up across reloads). Booleans: SQLite stores them as
    0/1 and the worker returns numbers, while the web driver's `getBoolean` expects a JS boolean, so store flags as
    `INTEGER` and convert in the mappers (or prove a `Boolean` column works on the web before relying on it).
