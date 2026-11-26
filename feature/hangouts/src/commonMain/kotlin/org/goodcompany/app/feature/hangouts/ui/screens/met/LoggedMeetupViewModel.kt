package org.goodcompany.app.feature.hangouts.ui.screens.met

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.goodcompany.app.core.domain.usecase.LoggedMeetup
import org.goodcompany.app.core.domain.usecase.LoggedMeetupInbox
import org.goodcompany.app.core.domain.usecase.UndoLogUseCase

/**
 * The app-wide "Logged time with Rui ✨ · Undo" snackbar (issue #47): the Log popup closes on
 * save, so the meetup it wrote waits in [LoggedMeetupInbox] until this shows it. Undo removes the hangout and, for a
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
