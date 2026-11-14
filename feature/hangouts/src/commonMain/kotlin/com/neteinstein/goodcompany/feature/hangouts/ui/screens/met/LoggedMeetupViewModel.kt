package com.neteinstein.goodcompany.feature.hangouts.ui.screens.met

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neteinstein.goodcompany.core.domain.usecase.LoggedMeetup
import com.neteinstein.goodcompany.core.domain.usecase.LoggedMeetupInbox
import com.neteinstein.goodcompany.core.domain.usecase.UndoLogUseCase
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * The app-wide "Logged time with Rui ✨ · Undo" snackbar (issue #47): the "We met" panel and the Log sheet close on
 * save, so the meetup they wrote waits in [LoggedMeetupInbox] until this shows it. Undo removes the hangout and, for a
 * plan's "Did you meet?", reopens the plan.
 */
class LoggedMeetupViewModel(
    private val inbox: LoggedMeetupInbox,
    private val undoLog: UndoLogUseCase,
) : ViewModel() {
    val pending: StateFlow<LoggedMeetup?> = inbox.pending

    fun onUndo(meetup: LoggedMeetup) {
        inbox.clear(meetup)
        viewModelScope.launch { undoLog(meetup.result) }
    }

    fun onShown(meetup: LoggedMeetup) = inbox.clear(meetup)
}
