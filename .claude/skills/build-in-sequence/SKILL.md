---
name: build-in-sequence
description: "Build in sequence" - implement a list of already-planned GitHub issues one after another, each in its own Claude Code session and worktree, where every branch is cut from the previous session's branch (stacked branches + stacked PRs, so no merge conflicts between them). All questions are asked up front so the sessions run without the owner. Use when the owner says "build in sequence" / "build these in sequence" / "implementa em sequência" and gives issue numbers.
---

# Build in sequence

You are the **orchestrator**. You never implement an issue here: each issue gets its own session. The sessions run
**one at a time**; session *n+1* starts only after session *n* has pushed its branch and opened its PR, and its worktree
is cut from session *n*'s branch. The owner answers questions **only in phase 1**; after that the chain runs alone.

Input: an ordered list of issue numbers (e.g. `#35 #36 #38`). The order given is the build order. If none is given, ask.

## Phase 1: preflight (the only phase that talks to the owner)

1. **Read everything first.** `AGENTS.md`, `docs/PRODUCT_BRIEF.md` (verbatim brief, then the Decisions rows its "Index by area" lists for the issues' areas) and, for each
   issue, `gh issue view <n> --comments` (body, acceptance criteria, linked mockups/plans in `docs/`). Note dependencies
   between issues: if the given order builds something before what it depends on, say so and propose the fixed order.
2. **Check the starting point.** `git fetch`; the chain starts from the current branch unless the owner names another
   (default: `main`, up to date with `origin`). The working tree must be clean of anything the first session needs:
   uncommitted files are not in a worktree.
3. **Collect every open question, for all issues at once.** Anything a session would otherwise have to ask: ambiguous
   behaviour, copy not in the brief or the issue, a schema/migration choice, a platform the issue doesn't mention
   (Android / iOS / web parity), new permissions or dependencies, scope cuts. Don't ask what the brief, the issue, a
   Decision or a sensible repo convention already settles. Ask with `AskUserQuestion` (up to 4 questions per call;
   repeat until done), each question naming its issue (`#36: …`).
4. **Run-level settings** (ask in the same round, recommended option first):
   - PR target: **stacked** (PR *n* targets branch *n-1*, the first targets `main`; recommended) or every PR to `main`.
   - Draft PRs or ready for review.
   - On a session that ends **BLOCKED** or with failing checks: **stop the chain** (recommended) or skip that issue and
     go on (only possible when later issues don't depend on it).
5. **Write the run plan** to `<scratchpad>/build-in-sequence.md`: the order, the base branch, the settings, and per issue
   the owner's answers verbatim. New *product* decisions from the answers also go into the brief's Decisions table, dated
   — that edit is the first session's job (tell it in its brief), not yours, so it lands on a branch.
6. Show the plan in a few lines and start. No further confirmation is needed once the questions are answered.

## Phase 2: the chain

For issue *i* (1-based) with previous branch `prev` (for *i* = 1: the base branch):

1. **Start the session**, `mcp__ccd_session__start_session` with:
   - `initiation: "user_asked"` (the owner asked for the chain), `context: "fresh"`, `use_worktree: true`,
     `wake_when_done: true`, `source_branch: <prev>`, `branch_name: "v1-<n>-<slug>"`, `name: "issue-<n>"`,
     `title: "#<n> <issue title>"`. Same model/effort as yours unless the owner said otherwise.
   - `background`: the goal of the whole run, the full order, which issues are already done (their branches/PRs) and
     that this branch already contains their work.
   - `prompt`: the session brief below, filled in.
2. **Wait.** Don't poll. The app continues you with a `<side-sessions-event>` when the session ends its turn.
3. **Verify, don't trust the note.** The report must start with `DONE` or `BLOCKED`. For `DONE`, check yourself:
   `git ls-remote --heads origin <branch>` exists, `gh pr view <branch> --json url,baseRefName,state` matches the
   settings, and `git log --oneline <prev>..origin/<branch>` has the work (and `<prev>` is its ancestor).
   Then `prev = <branch>` and go to issue *i+1*.
4. **On `BLOCKED`, a failed check, or a session waiting on a question:** apply the setting from phase 1. When stopping,
   report to the owner: what's done (branches, PRs), what blocked and why, what's left. Never answer a product question
   for the owner yourself.
5. **At the end**, report the stack in order: issue → branch → PR → base, plus each PR's skills loaded and "Autonomous decisions".
   Remind the owner to merge **in order, bottom first, with merge commits** (the repo merges with merge commits). After a
   lower PR merges and its branch is deleted, GitHub retargets the next PR to `main`. If a lower PR changes during
   review, the higher branches need a rebase/merge from it: offer to run that as a session.

## Session brief (the `prompt`)

> Implement GitHub issue #<n> (`gh issue view <n> --comments`) in this worktree. Your branch is cut from `<prev>`, which
> already contains <list of earlier issues or "nothing extra">; build on that code, don't redo or revert it.
>
> **Owner answers for this issue (final, don't re-ask):** <verbatim answers, or "none needed">.
> <For the first session only: add these dated rows to the Decisions table in `docs/PRODUCT_BRIEF.md`: …>
>
> **Rules.** Follow `AGENTS.md` exactly (MVVM, Koin in `AppModule.kt`, strings in all five languages, migrations,
> tests with every domain/data change, design system first). The brief wins over the issue when they disagree.
> **Don't ask the owner anything; nobody is watching.** If something is still unclear, take the most conservative choice
> that fits the brief, the issue and the existing code, and list it under "Autonomous decisions" in the PR. Only stop if
> continuing would mean guessing a product decision with real consequences (data loss, a new permission, a store-policy
> risk, a big scope change): then commit what is sound, push, and report `BLOCKED: <the question>`.
> Stay inside this issue's scope; note unrelated problems in the PR instead of fixing them.
>
> **Skills.** Before coding, load the `area-*` skills for every area the issue touches, and the checklists that apply
> (`persistence-change`, `new-screen`, `add-strings`, `ios-project-edit`). New area notes go into those skills.
>
> **Done means:**
> 1. While iterating, use the fast loop in AGENTS.md ("Build & verify"): only the touched modules' tests. Before
>    pushing, run once: `./gradlew spotlessApply testDebugUnitTest koverVerify spotlessCheck` and
>    `python3 scripts/check-strings.py` (all of them green). Add `:androidApp:lintDebug :androidApp:assembleDebug` only
>    if you changed a manifest, Android `res/` or `androidMain`. Add `:composeApp:compileKotlinIosSimulatorArm64` only
>    for `iosMain`, `expect`/`actual` code or `iosApp/` (plus an `xcodebuild` of scheme `iosApp` for `iosApp/`). Add
>    `:composeApp:compileKotlinWasmJs` only for `wasmJsMain`. CI runs everything else. Don't refresh screenshots: list
>    the changed screens under "Screenshots to refresh" in the PR (gotcha 15).
> 2. Conventional Commits (`feat(<module>): …`), ending with the repo's attribution trailer.
> 3. `git push -u origin HEAD`, then a <draft/ready> PR with `gh pr create --base <prev or main> --title "<type>(<scope>): … (#<n>)"`
>    using `.github/pull_request_template.md`: "Closes #<n>", the test plan (what ran, what couldn't), risk, and an
>    "Autonomous decisions" section. In a stacked PR, say in the summary that it sits on <prev PR>.
> 4. Your final message starts with `DONE` or `BLOCKED`, then: branch name, PR URL, checks run and their result,
>    skills loaded, autonomous decisions. Don't wait for CI.
