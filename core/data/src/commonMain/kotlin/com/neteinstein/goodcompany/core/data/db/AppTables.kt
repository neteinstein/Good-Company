package com.neteinstein.goodcompany.core.data.db

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import com.neteinstein.goodcompany.core.domain.model.AppSettings
import com.neteinstein.goodcompany.core.domain.model.CircleColor
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.HangoutActivity
import com.neteinstein.goodcompany.core.domain.model.IdeaKind
import com.neteinstein.goodcompany.core.domain.model.InteractionSource
import com.neteinstein.goodcompany.core.domain.model.InteractionType
import com.neteinstein.goodcompany.core.domain.model.Place
import com.neteinstein.goodcompany.core.domain.model.PlanStatus
import com.neteinstein.goodcompany.core.domain.model.ConversationMessage as DomainMessage
import com.neteinstein.goodcompany.core.domain.model.FriendGroup as DomainGroup
import com.neteinstein.goodcompany.core.domain.model.Hangout as DomainHangout
import com.neteinstein.goodcompany.core.domain.model.Idea as DomainIdea
import com.neteinstein.goodcompany.core.domain.model.Interaction as DomainInteraction
import com.neteinstein.goodcompany.core.domain.model.JournalEntry as DomainJournalEntry
import com.neteinstein.goodcompany.core.domain.model.Plan as DomainPlan
import com.neteinstein.goodcompany.core.domain.model.Reminder as DomainReminder

// Read / write for everything except friends (FriendTables.kt). Writes go inside a transaction. Join rows only keep
// friends that exist: an unknown friend id is skipped (the old JSON stores kept it, but nothing could show it).
// The `friend` table is in every observer's list because deleting a friend removes their join rows by cascade.

internal val HANGOUT_TABLES = arrayOf("hangout", "hangout_friend", "friend")
internal val REMINDER_TABLES = arrayOf("reminder", "reminder_friend", "friend")
internal val IDEA_TABLES = arrayOf("idea", "idea_friend", "friend")
internal val PLAN_TABLES = arrayOf("plan", "plan_friend", "idea", "friend")
internal val GROUP_TABLES = arrayOf("friend_group", "group_member", "friend")
internal val INTERACTION_TABLES = arrayOf("interaction", "friend")
internal val MESSAGE_TABLES = arrayOf("conversation_message", "friend")
internal val JOURNAL_TABLES = arrayOf("journal_entry", "friend")
internal val PREFERENCE_TABLES = arrayOf("user_preferences")

private fun <T> List<T>.friendIdsBy(key: (T) -> String, friendId: (T) -> String): Map<String, List<FriendId>> =
    groupBy(key).mapValues { (_, rows) -> rows.map { FriendId(friendId(it)) } }

// --- Hangouts ---

internal suspend fun GoodCompanyDatabase.readHangouts(): List<DomainHangout> {
    val friends = hangoutQueries.allHangoutFriends().awaitAsList().friendIdsBy({ it.hangout_id }, { it.friend_id })
    return hangoutQueries.allHangouts().awaitAsList().map { row ->
        val place = if (row.place_name == null && row.place_latitude == null && row.place_longitude == null) {
            null
        } else {
            Place(row.place_name, row.place_latitude, row.place_longitude)
        }
        val activity = row.activity?.let { a -> HangoutActivity.entries.firstOrNull { it.name == a } }
        DomainHangout(row.id, friends[row.id].orEmpty(), row.at.toInstant(), place, row.note, activity)
    }
}

/** Adds or updates [hangout]; a new one goes to the end of the list. */
internal suspend fun GoodCompanyDatabase.writeHangout(hangout: DomainHangout) {
    val q = hangoutQueries
    q.upsert(
        Hangout(
            id = hangout.id,
            position = q.nextPosition().awaitAsOneOrNull() ?: 0L,
            at = hangout.at.toColumn(),
            place_name = hangout.place?.name,
            place_latitude = hangout.place?.latitude,
            place_longitude = hangout.place?.longitude,
            note = hangout.note,
            activity = hangout.activity?.name,
        ),
    )
    q.deleteFriends(hangout.id)
    hangout.friendIds.forEachIndexed { i, f -> q.insertFriend(hangout.id, f.value, i.toLong()) }
}

// --- Reminders ---

internal suspend fun GoodCompanyDatabase.readReminders(): List<DomainReminder> {
    val friends = reminderQueries.allReminderFriends().awaitAsList().friendIdsBy({ it.reminder_id }, { it.friend_id })
    return reminderQueries.allReminders().awaitAsList().map { row ->
        DomainReminder(row.id, row.title, friends[row.id].orEmpty(), row.due_at?.toInstant(), row.done.toFlag(), row.created_at.toInstant())
    }
}

/** Adds or updates [reminder]; an update keeps its place in the list, a new one goes to the end. */
internal suspend fun GoodCompanyDatabase.writeReminder(reminder: DomainReminder) {
    val q = reminderQueries
    q.upsert(
        Reminder(
            id = reminder.id,
            position = q.nextPosition().awaitAsOneOrNull() ?: 0L,
            title = reminder.title,
            due_at = reminder.dueAt?.toColumn(),
            done = reminder.done.toColumn(),
            created_at = reminder.createdAt.toColumn(),
        ),
    )
    q.deleteFriends(reminder.id)
    reminder.friendIds.forEachIndexed { i, f -> q.insertFriend(reminder.id, f.value, i.toLong()) }
}

// --- Ideas ---

internal suspend fun GoodCompanyDatabase.readIdeas(): List<DomainIdea> {
    val friends = ideaQueries.allIdeaFriends().awaitAsList().friendIdsBy({ it.idea_id }, { it.friend_id })
    return ideaQueries.allIdeas().awaitAsList().map { row ->
        DomainIdea(
            id = row.id,
            friendIds = friends[row.id].orEmpty(),
            kind = IdeaKind.valueOf(row.kind),
            title = row.title,
            description = row.description,
            url = row.url,
            whenText = row.when_text,
            createdAt = row.created_at.toInstant(),
            dismissed = row.dismissed.toFlag(),
            proposedAt = row.proposed_at?.toInstant(),
        )
    }
}

internal suspend fun GoodCompanyDatabase.writeIdea(idea: DomainIdea) {
    val q = ideaQueries
    q.upsert(
        Idea(
            id = idea.id,
            position = q.nextPosition().awaitAsOneOrNull() ?: 0L,
            kind = idea.kind.name,
            title = idea.title,
            description = idea.description,
            url = idea.url,
            when_text = idea.whenText,
            created_at = idea.createdAt.toColumn(),
            dismissed = idea.dismissed.toColumn(),
            proposed_at = idea.proposedAt?.toColumn(),
        ),
    )
    q.deleteFriends(idea.id)
    idea.friendIds.forEachIndexed { i, f -> q.insertFriend(idea.id, f.value, i.toLong()) }
}

// --- Plans ---

internal suspend fun GoodCompanyDatabase.readPlans(): List<DomainPlan> {
    val friends = planQueries.allPlanFriends().awaitAsList().friendIdsBy({ it.plan_id }, { it.friend_id })
    return planQueries.allPlans().awaitAsList().map { row ->
        DomainPlan(
            id = row.id,
            friendIds = friends[row.id].orEmpty(),
            title = row.title,
            date = row.date.toLocalDate(),
            place = row.place,
            ideaId = row.idea_id,
            status = PlanStatus.valueOf(row.status),
            createdAt = row.created_at.toInstant(),
        )
    }
}

/** Adds or updates [plan]. Its idea link is kept only while that idea exists. */
internal suspend fun GoodCompanyDatabase.writePlan(plan: DomainPlan) {
    val q = planQueries
    q.upsert(
        id = plan.id,
        position = q.nextPosition().awaitAsOneOrNull() ?: 0L,
        title = plan.title,
        date = plan.date.toColumn(),
        place = plan.place,
        ideaId = plan.ideaId,
        status = plan.status.name,
        createdAt = plan.createdAt.toColumn(),
    )
    q.deleteFriends(plan.id)
    plan.friendIds.forEachIndexed { i, f -> q.insertFriend(plan.id, f.value, i.toLong()) }
}

// --- Groups ---

internal suspend fun GoodCompanyDatabase.readGroups(): List<DomainGroup> {
    val members = friendGroupQueries.allMembers().awaitAsList().friendIdsBy({ it.group_id }, { it.friend_id })
    return friendGroupQueries.allGroups().awaitAsList().map { row ->
        DomainGroup(
            id = row.id,
            name = row.name,
            emoji = row.emoji,
            memberIds = members[row.id].orEmpty(),
            meetEveryDays = row.meet_every_days?.toInt(),
            snoozedUntil = row.snoozed_until?.toLocalDate(),
            color = CircleColor.parse(row.color),
        )
    }
}

internal suspend fun GoodCompanyDatabase.writeGroup(group: DomainGroup) {
    val q = friendGroupQueries
    q.upsert(
        Friend_group(
            id = group.id,
            position = q.nextPosition().awaitAsOneOrNull() ?: 0L,
            name = group.name,
            emoji = group.emoji,
            meet_every_days = group.meetEveryDays?.toColumn(),
            snoozed_until = group.snoozedUntil?.toColumn(),
            color = group.color.name,
        ),
    )
    q.deleteMembers(group.id)
    group.memberIds.forEachIndexed { i, f -> q.insertMember(group.id, f.value, i.toLong()) }
}

// --- Interactions and chat lines ---

internal suspend fun GoodCompanyDatabase.readInteractions(): List<DomainInteraction> =
    interactionQueries.allInteractions().awaitAsList().map {
        DomainInteraction(
            friendId = FriendId(it.friend_id),
            type = InteractionType.valueOf(it.type),
            at = it.at.toInstant(),
            id = it.id,
            note = it.note,
            source = InteractionSource.valueOf(it.source),
            durationSeconds = it.duration_seconds?.toInt(),
            batchId = it.batch_id,
            messageCount = it.message_count?.toInt(),
        )
    }

internal suspend fun GoodCompanyDatabase.readJournal(): List<DomainJournalEntry> =
    interactionQueries.allJournal().awaitAsList().map { DomainJournalEntry(it.id, FriendId(it.friend_id), it.at.toInstant(), it.text) }

/** Adds or replaces [entry] (skipped when its friend is gone). */
internal suspend fun GoodCompanyDatabase.writeJournal(entry: DomainJournalEntry) {
    interactionQueries.upsertJournal(entry.id, entry.friendId.value, entry.at.toColumn(), entry.text)
}

internal suspend fun GoodCompanyDatabase.readMessages(): List<DomainMessage> =
    interactionQueries.allMessages().awaitAsList().map {
        DomainMessage(FriendId(it.friend_id), it.at.toInstant(), it.from_me.toFlag(), it.text)
    }

/**
 * Adds [interaction] unless it's already stored (same id, or an automatic row for the same friend, type and time).
 * One without an id (an old backup) gets its stable automatic id.
 */
internal suspend fun GoodCompanyDatabase.writeInteraction(interaction: DomainInteraction) {
    val i = interaction.withId()
    interactionQueries.insertInteraction(
        id = i.id,
        friendId = i.friendId.value,
        type = i.type.name,
        at = i.at.toColumn(),
        note = i.note,
        source = i.source.name,
        durationSeconds = i.durationSeconds?.toColumn(),
        batchId = i.batchId,
        messageCount = i.messageCount?.toColumn(),
    )
}

/** Adds [message] unless the same one is already stored. */
internal suspend fun GoodCompanyDatabase.writeMessage(message: DomainMessage) {
    interactionQueries.insertMessage(message.friendId.value, message.at.toColumn(), message.fromMe.toColumn(), message.text)
}

// --- User preferences (the part of AppSettings that lives in the database) ---

/** The stored preferences applied onto [base]; [base] as it is when none were saved yet. */
internal suspend fun GoodCompanyDatabase.readPreferences(base: AppSettings): AppSettings {
    val row = userPreferencesQueries.get().awaitAsOneOrNull() ?: return base
    return base.copy(
        innerCircleDays = row.inner_circle_days.toInt(),
        closeCircleDays = row.close_circle_days.toInt(),
        goodFriendsDays = row.good_friends_days.toInt(),
        weeklyDigest = row.weekly_digest.toFlag(),
        digestDay = row.digest_day.toInt(),
        reminderHour = row.reminder_hour.toInt(),
        weeklyIdeasEnabled = row.weekly_ideas_enabled.toFlag(),
        homeArea = row.home_area,
        showWarmth = row.show_warmth.toFlag(),
        privateNotifications = row.private_notifications.toFlag(),
        homeCircleFilter = row.home_circle_filter,
    )
}

internal suspend fun GoodCompanyDatabase.writePreferences(settings: AppSettings) {
    userPreferencesQueries.put(
        User_preferences(
            id = 1L,
            inner_circle_days = settings.innerCircleDays.toColumn(),
            close_circle_days = settings.closeCircleDays.toColumn(),
            good_friends_days = settings.goodFriendsDays.toColumn(),
            weekly_digest = settings.weeklyDigest.toColumn(),
            digest_day = settings.digestDay.coerceIn(1, 7).toColumn(), // the schema's CHECKs; old JSON was never range-checked
            reminder_hour = settings.reminderHour.coerceIn(0, 23).toColumn(),
            weekly_ideas_enabled = settings.weeklyIdeasEnabled.toColumn(),
            home_area = settings.homeArea,
            show_warmth = settings.showWarmth.toColumn(),
            private_notifications = settings.privateNotifications.toColumn(),
            home_circle_filter = settings.homeCircleFilter,
        ),
    )
}
