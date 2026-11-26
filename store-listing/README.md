# Play Store listing assets

> **This listing is for the Play build** (`release` build type, application id **`org.goodcompany.app`**).
> The `sideload` build (`org.goodcompany.app.sideload`) adds call-log matching, the contact list, contact
> write-back and self-update, which Play restricts. It is attached to GitHub releases and **never uploaded
> to Play** (see [CI_CD.md](../CI_CD.md)). Describe only what the Play build does everywhere in this folder:
> people are added with Android's contact picker, the app never reads the call log or SMS.

Everything needed to fill in the **Main store listing** (and get a head start on **App content > Data safety**
and **App access**) for `org.goodcompany.app` in the [Google Play Console](https://play.google.com/console)
lives in this folder.

## What's here

```
store-listing/
├── text/
│   ├── en-US/   title.txt, short_description.txt, full_description.txt, release_notes.txt
│   ├── pt-PT/   same four files, European Portuguese
│   ├── es-ES/   Spanish (Spain)
│   ├── fr-FR/   French (France)
│   └── ro/      Romanian
├── graphics/
│   ├── icon-512.png                  512x512 hi-res icon
│   └── feature-graphic-1024x500.png  1024x500 feature graphic
├── screenshots/
│   ├── phone/                        8 captioned 1080x1920 screenshots
│   ├── sevenInchTablet/              same 8 moments, 1920x1080 "large screen" layout
│   └── tenInchTablet/                same 8 moments, 2560x1440 "large screen" layout
├── scripts/store_assets/build.sh     regenerates everything under graphics/ + screenshots/ (needs Playwright)
├── data-safety.md                    cheat-sheet for the Data safety questionnaire
└── testing-instructions.md           App access instructions + internal-track QA checklist
```

## Copy-pasting the text fields

| Play Console field | File | Limit | Current length |
|---|---|---|---|
| App name | `text/<locale>/title.txt` | 30 chars | 25 en-US · 27 pt-PT · 27 es-ES · 25 fr-FR · 29 ro |
| Short description | `text/<locale>/short_description.txt` | 80 chars | 72 en-US · 76 pt-PT · 77 es-ES · 80 fr-FR · 74 ro |
| Full description | `text/<locale>/full_description.txt` | 4000 chars | 2858 en-US · 3205 pt-PT · 3206 es-ES · 3386 fr-FR · 3228 ro |
| What's new (this release) | `text/<locale>/release_notes.txt` | 500 chars | 294 en-US · 333 pt-PT · 342 es-ES · 338 fr-FR · 342 ro |

Paste each file's contents verbatim into the matching field for that locale. Play's locale codes are
`en-US`, `pt-PT`, `es-ES`, `fr-FR` and `ro`; they match the app's five languages (`values`, `values-pt`,
`values-es`, `values-fr`, `values-ro`). Set **en-US** as the default language.

The store title is **"Good Company: Friends CRM"**: "Friends CRM" is how the brief describes the app and what
people search for. The name on the device stays "Good Company"; Play allows the two to differ.

The translations reuse the app's own words from each `strings.xml` (closeness levels *Íntimos / Próximos /
Amigos / Conhecidos*, *Círculos*, *Quem está livre para…?*, *O teu ano em amizade*, …) and its informal tone
(the *tu* form in every language). pt-PT is European Portuguese ("telemóvel", "ecrã").

Every line describes a feature that exists in the Play build. When the app changes, update `en-US` first, then
the four translations in the same change, like the app's strings (see the `add-strings` skill).

Re-check lengths after editing (from `store-listing/`):

```bash
python3 -c "
import pathlib
limits = {'title.txt': 30, 'short_description.txt': 80, 'full_description.txt': 4000, 'release_notes.txt': 500}
for d in sorted(pathlib.Path('text').iterdir()):
    for name, limit in limits.items():
        n = len((d / name).read_text(encoding='utf-8').rstrip('\n'))
        print(f'{d.name}/{name}: {n}/{limit}', 'OK' if n <= limit else 'OVER!')
"
```

## Graphics & screenshots

Everything here is ready to upload as-is.

**Icon** (`graphics/icon-512.png`, 512x512, 32-bit PNG). The brand mark from `docs/brand/app-icon.svg`
(two hearts on the coral gradient), full bleed. Play applies its own rounded mask, so the corners are square.

**Feature graphic** (`graphics/feature-graphic-1024x500.png`). The icon, the app name and the promise
("A little CRM for friendship"), next to two phones showing Today and a friend's page.

**Screenshots.** Each one pairs a headline with a real screen of the app in a phone frame. Upload them in this
order:

| # | File | Headline | Screen (`docs/screenshots/…`) |
|---|---|---|---|
| 1 | `01-today.png` | Never lose touch with your people | Today tab (`agenda.png`) |
| 2 | `02-friend.png` | Everything about a friend | A friend's page (`friend.png`) |
| 3 | `03-log.png` | Log a meetup in seconds | The Log sheet (`checkin.png`) |
| 4 | `04-people.png` | Your people, by closeness | People tab (`home.png`) |
| 5 | `05-story.png` | Catch up before you meet | The catch-up story (`story.png`) |
| 6 | `06-circles.png` | Keep your circles together | Circles tab (`together.png`) |
| 7 | `07-plans.png` | Plans and gift lists | Plans tab (`ideas.png`) |
| 8 | `08-review.png` | Your year in friendship | Year in friendship (`review.png`) |

Play shows up to 8 phone screenshots, so all slots are used.

**How they are made.** The app screens are the **real Compose renders** in `docs/screenshots/`, produced by the
`capture-screenshots` skill from the web build with the sample friends (every name in them is fictional).
`scripts/store_assets/store.html` only adds the background, the headline and the phone frame, in the brand coral
and the app's font (Plus Jakarta Sans, loaded from `core/ui`); `render.js` renders them with headless Chromium.
Nothing in the app UI is redrawn or edited, so refresh `docs/screenshots/` first (its own `docs(screenshots)` PR),
then rebuild these. The headlines are English in every locale; Play falls back to the default-language
screenshots, so localized sets are optional.

They meet Play's requirements:
- **Phone:** 1080x1920, 24-bit PNG, no alpha, 8 images (Play needs at least 2, allows 8).
- **Tablets:** `screenshots/sevenInchTablet/` (1920x1080) and `screenshots/tenInchTablet/` (2560x1440) cover the
  **7-inch** and **10-inch tablet** slots, under "Large screens" in Play Console. 16:9 landscape, both dimensions
  between 1080 and 7680px, 8 images each. They show the phone screens beside the headline; uploading them is
  optional but recommended, since the manifest doesn't restrict the app to phones.

To change copy, layout or colours, edit `store.html`, then regenerate everything:

```bash
store-listing/scripts/store_assets/build.sh                 # all graphics and screenshots
store-listing/scripts/store_assets/build.sh --only 02-friend  # one screenshot, all three sizes
```

`build.sh` needs Node with Playwright (Chromium) and Python 3 with Pillow.

## Video

None yet. The **Video** field is optional and takes a YouTube URL. No permissions declaration form needs a demo
video: the Play build requests no restricted permission (CI fails the build if it declares SMS, call-log,
contacts, install-packages or `AD_ID` permissions).

## Suggested Play Console fields not covered by a file

- **App category:** Lifestyle (Social is the alternative; Good Company has no social network of its own)
- **Tags:** friends, relationships, birthdays, reminders, personal CRM
- **Content rating:** Everyone. Run the questionnaire: no ads, no user-to-user communication or sharing between
  users, no gambling. The optional AI features use the user's own Anthropic API key.
- **Ads:** No ads.
- **Contact email:** ⚠️ not decided yet. Add a support address before submitting.
- **Privacy Policy URL:** ⚠️ **required, and there is no privacy policy yet.** Play rejects apps that request
  location, calendar or contacts data without one. Write `PrivacyPolicy.md` at the repo root (it can follow
  [`data-safety.md`](data-safety.md) and the README's Privacy section) and use
  `https://github.com/neteinstein/Good-Company/blob/main/PrivacyPolicy.md`.
- **Website:** `https://github.com/neteinstein/Good-Company`

## Before the first upload

- **Package name is final once uploaded.** Play ties the listing to `org.goodcompany.app` for good. The
  Firebase project and the Google Drive OAuth clients must be registered for this package (and for
  `org.goodcompany.app.sideload`): see [docs/GOOGLE_DRIVE.md](../docs/GOOGLE_DRIVE.md). The real
  `google-services.json` (the `GOOGLE_SERVICES_JSON` secret) has to contain both package names, or the release
  build fails.
- **Google Drive backup** asks for the `drive.appdata` and `drive.file` scopes. Both are non-sensitive, but the
  OAuth consent screen still has to be published (and may need brand verification) before people outside the
  test users can sign in.
- **Upload an AAB**, signed with the upload key: `./gradlew :androidApp:bundleRelease` with a
  `keystore.properties` (see [CI_CD.md](../CI_CD.md)). Uploads are manual; there is no Play publishing workflow.
- **Data safety** and **App access** must be filled in before review: see [`data-safety.md`](data-safety.md) and
  [`testing-instructions.md`](testing-instructions.md).
- **Location permission.** The Play build asks for location only while the app is in use (naming a place when
  you log a meetup). No background location, so no extra declaration.
