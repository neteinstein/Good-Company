package com.neteinstein.goodcompany.feature.ideas.ui.screens.plans

import com.neteinstein.goodcompany.core.domain.usecase.GiftListSummary

/**
 * The rows of the Plans tab, in order. The tab is one list built from these, so it can scroll to a gift list by its
 * index (issue #104: a person's "Gift lists" link, a new or duplicated list) even while that row isn't on screen.
 */
sealed interface PlansRow {
    val key: String

    data object Header : PlansRow {
        override val key = "header"
    }

    data class Review(val year: Int) : PlansRow {
        override val key = "review"
    }

    data object PlansHeader : PlansRow {
        override val key = "plans-h"
    }

    data object PlansEmpty : PlansRow {
        override val key = "plans-empty"
    }

    data object OpenPlans : PlansRow {
        override val key = "plans"
    }

    data object CancelledHeader : PlansRow {
        override val key = "cancelled-h"
    }

    data object Cancelled : PlansRow {
        override val key = "cancelled"
    }

    data object GiftsHeader : PlansRow {
        override val key = GiftListsSection.HEADER
    }

    data object GiftsEmpty : PlansRow {
        override val key = "gift-lists-empty"
    }

    data class GiftList(val summary: GiftListSummary, val expanded: Boolean, val past: Boolean) : PlansRow {
        override val key = keyOf(summary.list.id)
    }

    data object PastGiftsHeader : PlansRow {
        override val key = "gift-lists-past"
    }

    data object WhosFree : PlansRow {
        override val key = "free"
    }

    data object Ideas : PlansRow {
        override val key = "ideas"
    }

    companion object {
        fun keyOf(giftListId: String) = "gift-list-$giftListId"
    }
}

/** What the Plans tab shows, top to bottom. */
fun plansRows(state: PlansUiState): List<PlansRow> = buildList {
    add(PlansRow.Header)
    state.reviewYear?.let { add(PlansRow.Review(it)) }
    add(PlansRow.PlansHeader)
    if (!state.isLoading && state.plans.isEmpty()) add(PlansRow.PlansEmpty)
    if (state.plans.isNotEmpty()) add(PlansRow.OpenPlans)
    if (state.cancelled.isNotEmpty()) {
        add(PlansRow.CancelledHeader)
        add(PlansRow.Cancelled)
    }
    val gifts = state.gifts
    add(PlansRow.GiftsHeader)
    if (!state.isLoading && gifts.isEmpty) add(PlansRow.GiftsEmpty)
    gifts.current.forEach { add(PlansRow.GiftList(it, it.list.id in gifts.expanded, past = false)) }
    if (gifts.past.isNotEmpty()) {
        add(PlansRow.PastGiftsHeader)
        gifts.past.forEach { add(PlansRow.GiftList(it, it.list.id in gifts.expanded, past = true)) }
    }
    add(PlansRow.WhosFree)
    // F1: nothing AI shows while AI is off.
    if (state.aiEnabled) add(PlansRow.Ideas)
}

/** The row [GiftListsSection.scrollTo] points at: a list's index, or the section's; null when it isn't shown. */
fun List<PlansRow>.indexOfGiftTarget(target: String): Int? {
    val key = if (target == GiftListsSection.HEADER) target else PlansRow.keyOf(target)
    return indexOfFirst { it.key == key }.takeIf { it >= 0 }
}
