# Good Company

**A CRM for your friends.** Kotlin Multiplatform + Compose Multiplatform app for **Android, iOS and the Web**.

- 💛 **Real friends**: pull your address book, then heart the people who matter.
- 🎂 **Birthdays, theirs and their kids'**, with what each one likes, so you never show up to a party empty-handed.
- 📝 **Likes and dislikes**, notes, and important moments ("surgery on Friday"), with a nudge to check in.
- 👋 **"I'm with…"**: one tap to log who you're with, where (typed or GPS), and when.
  See the last time you were with a particular group of people.
- ⏰ **Stay-in-touch rules**: "if I don't see Ana for a month, remind me". Plus free-form reminders
  ("book tickets for the concert with Rui").
- 💬 **Conversations**: match the call log (sideload build) and imported WhatsApp chat exports to friends.
- ✨ **AI ideas** (your own Anthropic API key): news and conversation starters matched to each friend's tastes,
  and a weekly run that finds local events to propose together, per friend opt-in.

Everything is stored on your device. SMS are never read.

| Platform | Run |
|---|---|
| Android | `./gradlew :androidApp:installDebug` |
| iOS | open `iosApp/iosApp.xcodeproj` in Xcode, scheme **iosApp** |
| Web | `./gradlew :composeApp:wasmJsBrowserDevelopmentRun` |

Contributors and agents: read **[AGENTS.md](AGENTS.md)** (architecture, conventions, gotchas) and
**[CI_CD.md](CI_CD.md)**. The product spec is **[docs/PRODUCT_BRIEF.md](docs/PRODUCT_BRIEF.md)**.
