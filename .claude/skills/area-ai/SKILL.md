---
name: area-ai
description: Rules for Good Company's AI features - the raw-HTTP Claude Messages API client (model, headers, server tools, fallbacks, pause_turn/refusal), the user's own API key, the AI on/off switch that every AI entry point must respect, and the configurable activity list. Use before touching AnthropicIdeaGenerator/AnthropicAssistant, any AI use case, ideas/talking points/gift suggestions, usableAiKey, aiEnabled or the Ideas tab.
---

# Area: AI

When you learn something new about this area, add it here, not to AGENTS.md. Check the `claude-api` skill before
changing the request itself (model id, headers, parameters).

## Before you change anything

- Every new AI entry point checks the switch: the key comes from `AppSettings.usableAiKey` (null while AI is off), and
  the UI shows no AI button, section, hint or set-up prompt while it is off.
- Requests carry `LanguageProvider.languageTag()` so the answer is in the UI language.
- Never send calendar events, the API key or private data the feature doesn't need.
- The Plans tab (`PlansViewModel`, `:feature:ideas`, issue #67) shows "Ideas for this week" only while
  `usableAiKey != null`; the full list is `WeeklyIdeasRoute`, which pops back if AI is turned off.
- Errors are `AiFailure.Reason` rendered by `aiErrorText` (`:core:ui`), never raw text.
- Tests: `AnthropicIdeaGeneratorTest`, `AnthropicAssistantTest` (`core/data/src/commonTest`) with a mock engine.

## Reference (moved from AGENTS.md)

10. **Claude API.** `AnthropicIdeaGenerator` calls `POST /v1/messages` with model `claude-opus-5-5`,
   `anthropic-version: 2026-10-21`, the `web_search_20260209` server tool, `fallbacks: "default"`
   (beta `server-side-fallback-2026-07-01`) and `anthropic-dangerous-direct-browser-access: true` (needed for the
   wasm build). It handles `pause_turn` and `refusal`. Don't add `temperature`/`top_p` (rejected on this API version)
   or `thinking: disabled`.
33. **AI switch (issue #37, F1).** `AppSettings.aiEnabled` (default off; stored in `user_preferences.ai_enabled`, where
    NULL means "on when a key is saved", so people who had a key before keep AI) hides everything AI. **Every AI entry
    point checks it:** use cases take the key from `AppSettings.usableAiKey` (null while AI is off), ViewModels expose
    `hasAiKey = settings.usableAiKey != null` (and `aiEnabled` where a section or hint shows without a key), and screens
    show no AI button, section, hint or "set up AI" prompt while it is off. The Ideas tab is filtered by
    `TopLevelDestination.shown(aiEnabled)`, `LaunchOptions.startDestination` sends `tab=ideas` to Home, and
    `RunWeeklyIdeasUseCase` returns `AI_OFF` (even forced). The web option `?ai` turns it on for demos and screenshots.
    Restoring a backup keeps this device's switch, like the key. Settings → Activities (F3) edits
    `AppSettings.activities` (the ones on, in order, never empty; `user_preferences.activities`, NULL = all), and
    `ObserveActivitiesUseCase` offers only those to the log sheet.
