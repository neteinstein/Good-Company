# Matching friends on social networks: viability plan

**Status:** direction agreed 2026-11-08 (see §0), nothing built yet. Written 2026-11-08 alongside the catch-up story, which is the natural consumer
of anything we could pull from social networks: a friend's new job, a move or a new baby would show up as a
"What's new" item and lead their story.

**Question:** can Good Company (a) match a friend to their accounts on social networks and (b) fetch what's new in
their life from there, without a server of our own (current architecture: on-device data, user's own Anthropic key)?

**Short answer:** not from the big closed networks (Instagram, Facebook, LinkedIn, TikTok, Snapchat, WhatsApp
status). Their APIs either were shut down for this use or only return the signed-in user's own content. What *is*
viable is (1) open networks with public read APIs (Bluesky, Mastodon), (2) the user sharing posts *into* the app
from the share sheet, and (3) the user pasting a screenshot or text that Claude turns into "What's new" items. Those
three cover most of the value with no ToS risk and no server.

## 0. Owner decisions (2026-11-08)
- ✅ **Share into the app** is the way to get updates from Instagram, LinkedIn and other closed networks, instead of
  complex integrations.
- ❌ **Bluesky and Mastodon: not integrating for now.** They're not where these friends post, so the phase is dropped.
- ✅ **RSS for blogs, Substack, Letterboxd and similar** is worth doing. It now has its own small fetch pipeline
  (it used to build on the Bluesky/Mastodon one).

---

## 1. Platform by platform

API facts move fast. Re-check each against the platform's own developer docs before building.

| Network | Can we match a friend automatically? | Can we read their posts / stories? | Verdict |
|---|---|---|---|
| **Instagram** | No. No contact or phone lookup API. | No. The Basic Display API (personal accounts) was shut down on 4 Dec 2024. The Graph API / Instagram Login only serves **business and creator** accounts, and only that account's own media. Stories of other people: no official route. Scraping breaks Meta's terms and gets accounts banned. | ❌ Not viable by API. ✅ Viable through **share-into-app** (§2.2). |
| **Facebook** | No. Friend lists only include friends who also use the same app (`user_friends`), with no profile data. | Only the signed-in user's own posts (`user_posts`, which needs app review). Friends' posts and life events: no. | ❌ Not viable. |
| **Threads** | No. | The Threads API returns the signed-in user's own threads (publishing and insights). Reading other people's posts at scale isn't offered for this use. | ❌ Not viable (re-check: Meta has been widening it). |
| **LinkedIn** | No. The connections API has been closed to non-partners for about a decade. "Sign In with LinkedIn" (OpenID Connect) returns only the signed-in user's own basic profile. | Job changes of connections: not exposed to consumer apps. Unofficial "LinkedIn APIs" (session-cookie automation) break LinkedIn's terms and risk the user's account. | ❌ Not viable. A job change is the single most useful signal, so the share/paste paths matter here. |
| **X (Twitter)** | Lookup by handle, yes. By phone or email, no. | Public posts are readable through the paid API (the free tier is write-mostly). Pricing has changed several times. Needs our own developer account and key, so we can't stay serverless with "bring your own Anthropic key" alone. | ⚠️ Technically possible, poor cost and fit. Defer. |
| **Bluesky (AT Protocol)** | Lookup by handle, yes (`app.bsky.actor.getProfile`). No phone or email matching, by design. | Yes. Public read endpoints on `public.api.bsky.app` need **no authentication**: `app.bsky.feed.getAuthorFeed` for a user's posts. Open protocol, explicitly meant for third-party clients. | ✅ **Viable now**, client-side, free. |
| **Mastodon / fediverse** | Lookup by handle (`@user@instance` → `/api/v1/accounts/lookup`). | Yes. Public statuses through `/api/v1/accounts/:id/statuses` on the user's instance, usually without auth. Respect each instance's rate limits and the account's `discoverable` / `noindex` flags. | ✅ **Viable now**, client-side. |
| **TikTok / Snapchat / WhatsApp status / BeReal** | No. | No read access to other people's content. | ❌ |
| **Strava, Goodreads, Letterboxd, personal blogs** | By handle or profile URL. | Often a public RSS feed (Letterboxd, Substack, Medium, blogs) or a public profile page. | ✅ Cheap win via **RSS**. Good for tastes ("watched…", "reading…"). |

**Matching by phone or email** (the "find my contacts on X" feature) is gone from all public APIs; it was a
privacy problem. So **matching is always the user's action**: they add a handle or profile link to a friend, or
pick from a short list of suggestions we search for by name (Bluesky/Mastodon search, user confirms).

## 2. What we could build (in order)

### 2.1 "Profiles" on a friend: handles and links (small, foundation)
- New `Friend.profiles: List<SocialProfile(network, handle, url)>`, added by hand on the friend page (also read from
  vCard `X-SOCIALPROFILE` / `URL` fields on import and contacts sync).
- Tapping a profile opens the app or the web page. Useful on its own and harmless. Stores only what the user typed.

### 2.2 Share into Good Company (biggest value, works with Instagram/LinkedIn too)
- Register as a **share target** (Android `ACTION_SEND` text/image, iOS Share Extension, Web Share Target on the PWA).
- The user sees a friend's Instagram story ("got the job!") or a LinkedIn update, taps Share → Good Company →
  picks the friend → it becomes a **"What's new"** item (text and link; a screenshot is optional).
- With an AI key, Claude reads the shared text or **screenshot** (vision) and proposes a one-line update
  ("Started as design lead at Studio X"), plus kids' names, dates or a new like. The user confirms before saving.
- No platform API, no scraping: the user is moving content they can already see. Fits every store policy.
- Cost: an Android intent filter plus a picker screen; on iOS a **new Share Extension target** in the hand-authored
  Xcode project (with an app group, the same blocker as the widget, see AGENTS.md gotcha 19); on the web, a
  manifest `share_target`.

### 2.3 Bluesky and Mastodon "what's new" fetch (open networks) — *dropped for now (owner, 2026-11-08)*

Kept for reference; not on the roadmap.

- For friends with a Bluesky or Mastodon handle, fetch recent public posts on open (and in the weekly job), straight
  from the device: no key and no server.
- Don't dump posts into the story. Send the last ~20 posts to Claude with the friend's profile and ask for **life
  updates only** (new job, move, baby, trip, injury, exam…) as structured output, de-duplicated against existing
  "What's new" items. Show them as **suggestions** ("From Bluesky: moved to Porto. Add?").
- Without an AI key: show the 3 latest posts on the friend page, with no extraction.
- Respect `noindex` / opt-out labels and only read public posts. Cache by post id and keep only the extracted line.

### 2.4 RSS for blogs, Letterboxd, Substack, Goodreads (cheap) — *agreed*
- A friend gets one or more **feed links** (from 2.1's profiles, or a pasted blog URL; the app finds the feed through
  `<link rel="alternate" type="application/rss+xml">` or common paths such as `/feed`, `/rss`).
- Fetch RSS/Atom on app open (at most daily per feed) and in the weekly job, straight from the device. Parse only
  title, link, date and a short summary; remember the last seen item per feed.
- New items show on the friend page as "From their blog: …". With an AI key, Claude picks the ones that are life
  updates or tastes and suggests a one-line "What's new" item or a like, for the user to confirm.
- Web build: many feeds don't send CORS headers, so the browser can't read them. The web build either skips feeds
  or only supports CORS-friendly ones (Substack, most WordPress sites). Mobile has no such limit.

### 2.5 Explicitly not doing
- Scraping Instagram, Facebook or LinkedIn, or "unofficial API" services that log in with the user's cookies: this
  breaks the platforms' terms, risks the user's account, and conflicts with store review. Same spirit as the
  *never read SMS* decision.
- Server-side crawling: it would need a backend and turn us into a data processor for third parties' personal data.
- Web search for a named private individual ("what is Ana Silva up to"): unreliable (namesakes) and creepy. The
  existing `web_search` tool stays for events and news, never for people lookups.

## 3. Privacy, legal and store notes
- Everything stays on the device, like the rest of the app. Fetched posts are processed and dropped; only the
  user-confirmed one-liner is stored (and backed up like any "What's new" item).
- Friends' public posts are still personal data. Keep it user-initiated per friend (the handle is added
  deliberately), keep it minimal, and offer "forget this friend's profiles" (deleting the friend already does this).
- App Store / Play: share extensions and public API reads are routine. Declare network use in the privacy labels
  (`PrivacyInfo.xcprivacy`, Play data safety: "data from other apps, user-initiated").
- AI: posts go to Anthropic with the user's own key, exactly as WhatsApp chat excerpts do today.

## 4. Proposed phases and rough size

| Phase | What | Size | Depends on |
|---|---|---|---|
| 1 | Social profiles on a friend (+ vCard fields) | S | — |
| 2 | Android + web share target → "What's new" (text/link), friend picker | M | 1 optional |
| 3 | AI extraction from shared text/screenshot (vision) with confirm sheet | M | 2, AI key |
| 4 | iOS Share Extension (new target + app group in the hand-authored project) | M–L | 2 |
| ~~5~~ | ~~Bluesky + Mastodon fetch~~ — dropped for now (owner, 2026-11-08) | — | — |
| 5 | RSS/Atom feeds on a friend → "From their blog" + AI suggestions (on open + weekly job) | S–M | 1, AI key for suggestions |

Phases 2 + 3 deliver most of the user's ask ("don't forget the new job / the kids") for every network, including the
closed ones, because the user is already looking at those stories. Phase 5 (RSS) is the one automatic fetch.

## 5. Open questions for the owner
1. ~~Share into the app as the Instagram/LinkedIn path?~~ Yes (2026-11-08).
2. ~~Bluesky/Mastodon suggestions?~~ Not integrating (2026-11-08). For RSS and shared content, AI suggestions wait
   for a confirm tap (recommended; same rule).
3. iOS share extension: worth the app-group work now, or Android/web first (as with the widget)?

## Sources (checked 2026-11-08; mostly third-party summaries, re-verify against the official docs)
- Instagram Basic Display shutdown and personal-account limits: <https://www.getphyllo.com/post/instagram-api-guide>,
  <https://www.getphyllo.com/post/instagram-basic-display-api-deprecation-what-it-is-for-developers-and-businesses>
- Bluesky public, unauthenticated read endpoints: <https://dev.to/agenthustler/scraping-bluesky-in-2026-at-protocol-makes-it-surprisingly-easy-56d6>
- LinkedIn access and limits (the third-party automation context is exactly what we avoid):
  <https://www.activepieces.com/blog/linkedin-api-access-limits-and-errors-in-2026.md>
