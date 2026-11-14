---
name: add-strings
description: Add or change user-facing text in Good Company in all five languages (English, European Portuguese, Spanish, French, Romanian) - which strings.xml to use (feature module, :core:ui, composeApp, Android res for widgets/notifications/contacts, iOS InfoPlist/Localizable.strings), plurals, formatting args and typographic apostrophes, and how ViewModels hand text to screens. Use whenever a change adds any visible word.
---

# Add strings

No user-facing literal in Kotlin or Swift. Every English string lands in the same change in `values-pt`, `values-es`,
`values-fr` and `values-ro`.

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
for f in $(git diff --name-only | grep 'values/strings.xml'); do
  d=$(dirname $(dirname $f)); en=$(grep -c '<string \|<plurals ' $f)
  for l in pt es fr ro; do echo "$d/values-$l: $(grep -c '<string \|<plurals ' $d/values-$l/strings.xml) (en $en)"; done
done
```
Counts must match per file. A new iOS `.lproj` string needs all five folders too. Then build Android once
(`:androidApp:assembleDebug`): a missing translation or a bad placeholder fails there or in lint.
