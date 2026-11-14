---
name: add-strings
description: Add or change user-facing text in Good Company in all five languages (English, European Portuguese, Spanish, French, Romanian) - which strings.xml to use (feature module, :core:ui, composeApp, Android res for widgets/notifications/contacts, iOS InfoPlist/Localizable.strings), plurals, formatting args and typographic apostrophes, and how ViewModels hand text to screens. Use whenever a change adds any visible word.
---

# Add strings

No user-facing literal in Kotlin or Swift. Every English string lands in the same PR in `values-pt`, `values-es`,
`values-fr` and `values-ro`.

**Translate once, at the end.** While building, add English only. Wording usually changes a few times, and every
change would mean editing five files. When the feature works and the copy has settled, run
`python3 scripts/check-strings.py --todo`. It lists every missing key with its English text, per file. Translate them
in one pass. You can hand that list to a subagent on a cheaper model, together with sections 2 and 3 below and the
existing strings of the same file for tone. Then re-run the script until it prints "All translations complete."

## 1. Where the string goes

| Who shows it | File (each has `values/` + `values-pt|es|fr|ro/`, or `<lang>.lproj/`) |
|---|---|
| A screen of one feature | `feature/<name>/src/commonMain/composeResources/values*/strings.xml` → `…feature.<name>.resources.Res` |
| Shared words, dates, notifications, sample data, AI errors, `:core:ui` components | `core/ui/src/commonMain/composeResources/values*/strings.xml` (import its `Res` as `CoreRes` in features) |
| Navigation bar, app shell, iOS shortcut items | `composeApp/src/commonMain/composeResources/values*/strings.xml` |
| Android widget, app shortcuts | `androidApp/src/main/res/values*/strings.xml` |
| Android notification channel, contact write-back labels | `core/data/src/androidMain/res/values*/strings.xml` |
| iOS permission prompts | `iosApp/iosApp/<lang>.lproj/InfoPlist.strings` |
| iOS widget / Share Extension | `iosApp/NextUpWidget/<lang>.lproj/Localizable.strings`, `iosApp/ShareExtension/<lang>.lproj/Localizable.strings` |

Name keys `<area>_<what>` like their neighbours (`gift_lists_deleted`). Never reuse a key for a different meaning.

## 2. Format

- Arguments are positional: `%1$s`, `%2$d`. Same placeholders in every language.
- Plurals: `<plurals>`; English, Portuguese, Spanish, French `one` + `other`; **Romanian `one` + `few` + `other`**
  (`other` for 20+ takes "de": "%1$d de zile"). Call with `pluralStringResource` / `getPluralString`.
- Typographic apostrophes and quotes: `’` and `“ ”` in every language (no `«»`), never `\'`.
- Portuguese is **European** (pt-PT): "telemóvel", "ecrã", "registar", "contacto", and the informal *tu*
  ("a tua chave", "Adiciona…"), like the rest of the app.
- Search vocab lists (e.g. `kin_search_*`) are `|`-separated words; accents are folded when matching.

Translate the meaning in the app's tone (short, warm, no exclamation marks unless English has one). Read the existing
strings in the same file first and reuse their words for the same things (circle, closeness, meetup, catch up).

## 3. From ViewModel to text

ViewModels hold no text: one-off messages are a sealed `FooMessage`; the screen file has
`suspend fun FooMessage.text(): String = when (this) { … getString(Res.string.foo, arg) }`. Domain code emits structured
data (`NotificationContent`, `AiFailure.Reason`, `SampleTexts`) that `:core:ui` renders. AI requests carry
`LanguageProvider.languageTag()`.

## 4. Check

```bash
python3 scripts/check-strings.py          # exit 1 on any problem; CI runs it too
python3 scripts/check-strings.py --todo   # same, with the English text of each missing key
```
It checks every `values/strings.xml` (Compose resources and Android `res/`) and every iOS `en.lproj/*.strings` against
the four translations: missing and extra keys, matching placeholders, and plural quantities (Romanian `few`).
