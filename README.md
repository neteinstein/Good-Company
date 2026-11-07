<p align="center">
  <img src="docs/screenshots/banner.png" alt="Good Company on phones: home, a friend's page, the I'm-with check-in, the agenda in dark mode and AI ideas" width="100%">
</p>

<h1 align="center">Good Company</h1>

<p align="center">
  <b>A little CRM for friendship.</b><br>
  Remember what your friends love, their kids' birthdays and the last time you saw each other —<br>
  and get a gentle nudge before it's been too long.
</p>

<p align="center">
  <a href="https://github.com/neteinstein/Good-Company/actions/workflows/ci.yml"><img src="https://github.com/neteinstein/Good-Company/actions/workflows/ci.yml/badge.svg" alt="CI"></a>
  <img src="https://img.shields.io/badge/Kotlin-2.4-7F52FF?logo=kotlin&logoColor=white" alt="Kotlin 2.4">
  <img src="https://img.shields.io/badge/Compose_Multiplatform-1.12-4285F4?logo=jetpackcompose&logoColor=white" alt="Compose Multiplatform 1.12">
  <img src="https://img.shields.io/badge/platforms-Android_·_iOS_·_Web-E5574F" alt="Android, iOS, Web">
</p>

---

## ✨ What it does

<table>
  <tr>
    <td width="33%"><img src="docs/screenshots/home.png" alt="Home: greeting, coming up, time to catch up, real friends"></td>
    <td>
      <h3>Your people, at a glance</h3>
      <ul>
        <li>A greeting that tells you what matters this week — <i>“2 birthdays · 1 big day · 1 to catch up with”</i>.</li>
        <li><b>Coming up</b>: swipeable cards for birthdays (theirs <i>and their kids'</i>), big days and reminders.</li>
        <li><b>Time to catch up</b>: friends past your “meet every N days” goal, with a one-tap <i>We met</i>.</li>
        <li><b>Real friends</b>: pull in your address book, then tap ♥ on the people who actually matter.</li>
        <li>Search by name <i>or taste</i> — “jazz”, “F1”, “sushi”.</li>
      </ul>
    </td>
  </tr>
  <tr>
    <td><img src="docs/screenshots/friend.png" alt="A friend's page with quick actions and stay-in-touch ring"></td>
    <td>
      <h3>Everything about a friend</h3>
      <ul>
        <li>A page tinted in their own colours, with one-tap <b>Together now</b> (with a little heart burst 💛), <b>Call</b>, <b>Text</b>, <b>WhatsApp</b> and <b>Remind me</b>.</li>
        <li><b>Stay in touch</b> ring: days since you last met against your goal.</li>
        <li><b>Loves / not a fan of</b>, <b>kids</b> (names, birthdays, what each one likes — perfect before a party), <b>important moments</b> (“knee surgery on Friday”) and notes.</li>
        <li><b>Things to talk about</b>: AI-matched news and conversation starters from their tastes and recent chats.</li>
        <li>Import a WhatsApp chat export to keep last-contact dates and context.</li>
      </ul>
    </td>
  </tr>
  <tr>
    <td><img src="docs/screenshots/checkin.png" alt="The I'm with check-in sheet"></td>
    <td>
      <h3>One tap: “I'm with…”</h3>
      <ul>
        <li>Pick who you're with, optionally where (typed, or your location) and a note. Done.</li>
        <li>See <b>the last time you were with any group</b> — “Ana & Rui: last together 2 days ago at Hot Clube, 3× in total”.</li>
        <li>A monthly view of meetups, friends seen and your favourite spot.</li>
      </ul>
    </td>
  </tr>
  <tr>
    <td><img src="docs/screenshots/agenda.png" alt="Coming up agenda with week strip"></td>
    <td>
      <h3>Never miss what matters</h3>
      <ul>
        <li>A week strip and timeline of birthdays, kids' birthdays, big days, overdue catch-ups and your reminders.</li>
        <li>Reminders like <i>“Book tickets for the jazz festival with Ana & Rui”</i>, with undo on delete.</li>
        <li>Local notifications on Android and iOS at the hour you choose — nothing scheduled on a server.</li>
      </ul>
    </td>
  </tr>
  <tr>
    <td><img src="docs/screenshots/ideas.png" alt="AI ideas"></td>
    <td>
      <h3>Ideas, powered by Claude</h3>
      <ul>
        <li><b>Weekly plans</b>: once a week, real events near you that match the tastes of friends you opt in — grouped by who'd enjoy them together.</li>
        <li><b>News & talking points</b> per friend, grounded with web search and linked to the source.</li>
        <li>Bring your own Anthropic API key — stored only on your device.</li>
      </ul>
    </td>
  </tr>
</table>

### 🌗 Light & dark

<p align="center">
  <img src="docs/screenshots/home-dark.png" width="24%" alt="Home in dark mode">
  <img src="docs/screenshots/friend-dark.png" width="24%" alt="Friend page in dark mode">
  <img src="docs/screenshots/together-dark.png" width="24%" alt="Together in dark mode">
  <img src="docs/screenshots/agenda-dark.png" width="24%" alt="Agenda in dark mode">
</p>

### 👋 First run & settings

<p align="center">
  <img src="docs/screenshots/onboarding.png" width="24%" alt="Onboarding">
  <img src="docs/screenshots/together.png" width="24%" alt="Together">
  <img src="docs/screenshots/settings.png" width="24%" alt="Settings">
</p>

A short animated intro with permission primers *before* any system dialog, and an **“Explore with sample friends”**
option so you can try everything before importing your own contacts (remove the sample anytime in Settings).

### 🖥️ On the web, too

<p align="center"><img src="docs/screenshots/desktop.png" width="90%" alt="Good Company in a desktop browser with a navigation rail"></p>

Wide screens get a navigation rail and a centred content column. The web build stores data in the browser and adds
friends by hand (browsers have no address book).

> Screenshots are rendered from the **real shared Compose UI** (the web build, in headless Chromium) — the same code
> that runs on Android and iOS. Regenerate them with the [`capture-screenshots`](.claude/skills/capture-screenshots/SKILL.md) skill.

## 🎨 Design

- **Material 3**, a warm coral/teal/sunflower palette, **Material You dynamic colour** on Android 12+, and smooth animated theme switching.
- **Plus Jakarta Sans** throughout ([OFL](docs/licenses/PlusJakartaSans-OFL.txt)).
- **Motion with purpose**: shared-element avatar transitions, drifting colour blobs behind headers, a pulsing check-in button, a heart burst when you log time together, animated rings, counters and list changes.
- **Edge-to-edge** on Android (with splash screen, adaptive + themed icon, predictive back) and iOS (light/dark app icons, privacy manifest).
- **Usable**: undo for destructive actions, haptics, 48 dp touch targets, semantic headings and content descriptions, sticky A–Z list, search by taste.

## 🔒 Privacy

- No account, no server. Everything lives on your device.
- Contacts are read only to build your list. **SMS are never read.** The call log is only matched in the separate *sideload* Android build (Play restricts that permission).
- WhatsApp and Messenger can't be read by any app; you can import a WhatsApp chat **export** per friend.
- The only network traffic is the AI requests you trigger, sent with **your** key to `api.anthropic.com`.

## 🚀 Getting started

Requirements: JDK 21, Android SDK (API 37), and Xcode on macOS for iOS.

```bash
./gradlew :androidApp:installDebug
```

```bash
./gradlew :composeApp:wasmJsBrowserDevelopmentRun
```

For iOS, open `iosApp/iosApp.xcodeproj` in Xcode and run the **iosApp** scheme. The Kotlin framework is built automatically by a Run Script phase.

On the web, add `?demo` to the URL to load the sample circle (`?demo&tab=together&theme=dark` also works).

## 🧱 Architecture

```mermaid
flowchart TB
  subgraph Apps
    A[androidApp<br/>Activity · Application · Firebase]
    I[iosApp<br/>SwiftUI shell]
    W[wasmJs<br/>browser]
  end
  C[composeApp<br/>App · navigation · Koin AppModule]
  subgraph Features
    F1[friends] --- F2[hangouts] --- F3[agenda] --- F4[ideas] --- F5[settings] --- F6[onboarding]
  end
  UI[core:ui<br/>theme · components · platform hooks]
  D[core:data<br/>repositories · persistence · contacts · notifications · Claude client]
  DOM[core:domain<br/>models · use cases · repository interfaces]
  A --> C
  I --> C
  W --> C
  C --> Features
  C --> D
  Features --> UI
  Features --> DOM
  UI --> DOM
  D --> DOM
```

Kotlin Multiplatform with **commonMain-first** code, MVVM with `StateFlow`, Koin for DI, and one Gradle module per feature.
Tests live in `commonTest` and run on the JVM. See **[AGENTS.md](AGENTS.md)** for conventions and gotchas, and
**[CI_CD.md](CI_CD.md)** for the pipeline. The product spec is **[docs/PRODUCT_BRIEF.md](docs/PRODUCT_BRIEF.md)**.

| | |
|---|---|
| UI | Compose Multiplatform 1.12 · Material 3 · Navigation (type-safe) |
| Logic | Kotlin 2.4 · Coroutines/StateFlow · kotlinx-datetime/serialization |
| DI / Net | Koin 4.2 · Ktor 3.6 |
| Storage | JSON documents in multiplatform-settings (SharedPreferences · NSUserDefaults · localStorage) |
| AI | Claude Messages API with web search (user-supplied key) |
| Quality | kotlin.test · Kover · ktlint (Spotless) · Android Lint · R8 |

## 🤝 Contributing

Conventional Commits (`feat(friends): …`), a PR template with a mandatory test plan, and CI that builds Android, iOS
and Web, and runs the tests, coverage gate and linters on every PR.
