package org.goodcompany.app.feature.agenda.ui.screens

import org.goodcompany.app.core.domain.model.FriendId
import org.goodcompany.app.core.domain.usecase.ReminderTarget

/**
 * Issue #135: follows a reminder tap's [target]. The editor ([ReminderTarget.Edit]) is already open in the ViewModel;
 * a circle or a person opens their page.
 */
internal fun openReminderTarget(target: ReminderTarget, onOpenFriend: (FriendId) -> Unit, onOpenCircle: ((String) -> Unit)?) {
    when (target) {
        is ReminderTarget.Circle -> onOpenCircle?.invoke(target.circleId)
        is ReminderTarget.Person -> onOpenFriend(target.friendId)
        is ReminderTarget.Edit -> Unit
    }
}
