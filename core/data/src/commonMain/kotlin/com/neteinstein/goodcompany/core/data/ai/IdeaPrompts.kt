package com.neteinstein.goodcompany.core.data.ai

import com.neteinstein.goodcompany.core.domain.repository.IdeaRequest

/** Prompt text for the idea generator. Kept separate from transport so it can be unit-tested. */
internal object IdeaPrompts {
    val system = """
        You help someone be a better friend. You receive short profiles of their friends (likes, dislikes,
        kids, and sometimes recent chat lines) and you find timely, concrete, real things to share or do together.
        Use web search to ground every suggestion in something current and verifiable; prefer an official link.
        Never invent events, dates or URLs. Respect dislikes. Keep descriptions to one or two sentences,
        written as a friendly nudge to the user (e.g. "Ana loves jazz — ...").
        Finish with ONLY a JSON object, no prose after it, shaped exactly like:
        {"ideas":[{"friendIds":["<id>"],"kind":"event|news|talking_point","title":"...","description":"...","url":"https://... or null","when":"human date/time or null"}]}
    """.trimIndent()

    fun user(request: IdeaRequest): String = buildString {
        appendLine("Today is ${request.todayIso}.")
        when (request.mode) {
            IdeaRequest.Mode.WEEKLY_EVENTS -> {
                appendLine(
                    "Find up to 6 events happening in the next 3 weeks" +
                        (request.area?.let { " in or near $it" } ?: " near the user") +
                        " that would be a good plan to propose to one or more of these friends. " +
                        "Group friends with overlapping tastes into the same suggestion. Use kind \"event\".",
                )
            }
            IdeaRequest.Mode.TALKING_POINTS -> {
                appendLine(
                    "Find up to 5 recent news items or conversation starters this friend would genuinely enjoy, " +
                        "matched to their tastes and to what they've been talking about lately. " +
                        "Use kind \"news\" for news and \"talking_point\" for conversation starters." +
                        (request.area?.let { " The user lives around $it." } ?: ""),
                )
            }
        }
        appendLine()
        appendLine("Friends:")
        request.friends.forEach { f ->
            appendLine("- id=${f.id} name=${f.name}")
            if (f.likes.isNotEmpty()) appendLine("  likes: ${f.likes.joinToString()}")
            if (f.dislikes.isNotEmpty()) appendLine("  dislikes: ${f.dislikes.joinToString()}")
            if (f.kids.isNotEmpty()) appendLine("  kids: ${f.kids.joinToString()}")
            if (f.recentMessages.isNotEmpty()) {
                appendLine("  recent chat (oldest first):")
                f.recentMessages.forEach { appendLine("    $it") }
            }
        }
    }
}
