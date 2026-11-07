package com.neteinstein.goodcompany.feature.review.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neteinstein.goodcompany.core.domain.usecase.ObserveYearInReviewUseCase
import com.neteinstein.goodcompany.core.domain.usecase.YearInReview
import com.neteinstein.goodcompany.core.domain.util.AppClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.datetime.TimeZone

data class YearReviewUiState(
    val review: YearInReview? = null,
    val isLoading: Boolean = true,
    /** True right after "Share" fell back to the clipboard (web without the Web Share API). */
    val copied: Boolean = false,
    val zone: TimeZone = TimeZone.UTC,
)

/** "Your year in friendship" for [year] (null → the most relevant year, see `YearInReviewBuilder.defaultYear`). */
class YearReviewViewModel(year: Int?, observe: ObserveYearInReviewUseCase, clock: AppClock) : ViewModel() {
    private val local = MutableStateFlow(YearReviewUiState())

    val state: StateFlow<YearReviewUiState> = combine(observe(year), local) { review, ui ->
        ui.copy(review = review, isLoading = false, zone = clock.timeZone)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), YearReviewUiState())

    fun onCopied() = local.update { it.copy(copied = true) }

    fun onCopiedShown() = local.update { it.copy(copied = false) }
}
