package com.neteinstein.goodcompany.core.domain.usecase

import com.neteinstein.goodcompany.core.domain.model.Circle
import com.neteinstein.goodcompany.core.domain.model.ContactRecord
import com.neteinstein.goodcompany.core.domain.model.Friend
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.Interaction
import com.neteinstein.goodcompany.core.domain.model.InteractionType
import com.neteinstein.goodcompany.core.domain.model.NotificationAction
import com.neteinstein.goodcompany.core.domain.repository.AiAssistant
import com.neteinstein.goodcompany.core.domain.repository.FriendRepository
import com.neteinstein.goodcompany.core.domain.repository.GroupRepository
import com.neteinstein.goodcompany.core.domain.repository.IdeaRepository
import com.neteinstein.goodcompany.core.domain.repository.InteractionRepository
import com.neteinstein.goodcompany.core.domain.repository.LanguageProvider
import com.neteinstein.goodcompany.core.domain.repository.PhotoShrinker
import com.neteinstein.goodcompany.core.domain.repository.SettingsRepository
import com.neteinstein.goodcompany.core.domain.repository.photoOf
import com.neteinstein.goodcompany.core.domain.util.AppClock
import com.neteinstein.goodcompany.core.domain.util.IdGenerator
import kotlinx.coroutines.flow.first

/**
 * Marks/unmarks a real friend. Marking puts them in the Close circle by default, so catch-up reminders work with
 * one tap; unmarking clears the circle (their own "meet every" override, if any, is kept).
 */
class SetRealFriendUseCase(private val friends: FriendRepository) {
    suspend operator fun invoke(id: FriendId, real: Boolean) {
        val f = friends.get(id) ?: return
        friends.upsert(f.copy(isCloseFriend = real, circle = if (real) f.circle ?: Circle.CLOSE else null))
    }

    suspend fun toggle(id: FriendId) {
        val f = friends.get(id) ?: return
        invoke(id, !f.isCloseFriend)
    }
}

class SetCircleUseCase(private val friends: FriendRepository) {
    /** A circle implies "real friend"; null removes them from circles but keeps them a real friend. */
    suspend operator fun invoke(id: FriendId, circle: Circle?) {
        val f = friends.get(id) ?: return
        friends.upsert(f.copy(circle = circle, isCloseFriend = f.isCloseFriend || circle != null))
    }
}

/** Pauses a friend's (or group's) catch-up nudge. */
class SnoozeCatchUpUseCase(
    private val friends: FriendRepository,
    private val groups: GroupRepository,
    private val clock: AppClock,
) {
    suspend fun friend(id: FriendId, option: SnoozeOption) {
        val f = friends.get(id) ?: return
        friends.upsert(f.copy(snoozedUntil = option.until(clock.today())))
    }

    suspend fun group(id: String, option: SnoozeOption) {
        val g = groups.groups.first().firstOrNull { it.id == id } ?: return
        groups.upsert(g.copy(snoozedUntil = option.until(clock.today())))
    }

    suspend fun clearFriend(id: FriendId) {
        val f = friends.get(id) ?: return
        friends.upsert(f.copy(snoozedUntil = null))
    }
}

/** "Called them": logs a call (counts towards warmth and last contact) and snoozes the meet-up nudge for a week. */
class LogCallUseCase(
    private val interactions: InteractionRepository,
    private val snooze: SnoozeCatchUpUseCase,
    private val clock: AppClock,
) {
    suspend operator fun invoke(id: FriendId) {
        interactions.addAll(listOf(Interaction(id, InteractionType.CALL, clock.now())))
        snooze.friend(id, SnoozeOption.ONE_WEEK)
    }
}

/** Runs a quick action tapped on a catch-up notification. */
class HandleNotificationActionUseCase(
    private val checkIn: CheckInUseCase,
    private val logCall: LogCallUseCase,
    private val snooze: SnoozeCatchUpUseCase,
    private val groups: GroupRepository,
    private val confirmPlan: ConfirmPlanUseCase,
) {
    suspend operator fun invoke(action: NotificationAction) {
        if (action.isPlan) {
            confirmPlan(action.targetId, happened = action.kind == NotificationAction.Kind.PLAN_HAPPENED)
            return
        }
        if (action.isGroup) {
            when (action.kind) {
                NotificationAction.Kind.MET -> groups.groups.first().firstOrNull { it.id == action.targetId }?.let { g ->
                    if (g.memberIds.isNotEmpty()) checkIn(g.memberIds)
                }
                NotificationAction.Kind.SNOOZE_WEEK, NotificationAction.Kind.CALLED -> snooze.group(action.targetId, SnoozeOption.ONE_WEEK)
                NotificationAction.Kind.SNOOZE_WEEKEND -> snooze.group(action.targetId, SnoozeOption.WEEKEND)
                NotificationAction.Kind.PLAN_HAPPENED, NotificationAction.Kind.PLAN_SKIPPED -> Unit
            }
            return
        }
        val id = FriendId(action.targetId)
        when (action.kind) {
            NotificationAction.Kind.MET -> checkIn(listOf(id))
            NotificationAction.Kind.CALLED -> logCall(id)
            NotificationAction.Kind.SNOOZE_WEEK -> snooze.friend(id, SnoozeOption.ONE_WEEK)
            NotificationAction.Kind.SNOOZE_WEEKEND -> snooze.friend(id, SnoozeOption.WEEKEND)
            NotificationAction.Kind.PLAN_HAPPENED, NotificationAction.Kind.PLAN_SKIPPED -> Unit
        }
    }
}

/** Pauses (or resumes) catch-up nudges for a friend with no end date. History and birthdays are kept. */
class SetCatchUpPausedUseCase(private val friends: FriendRepository) {
    suspend operator fun invoke(id: FriendId, paused: Boolean) {
        val f = friends.get(id) ?: return
        friends.upsert(f.copy(catchUpPaused = paused, snoozedUntil = if (paused) null else f.snoozedUntil))
    }
}

/** Remembers that the app was opened, so catch-up nudges can back off when it's being ignored. */
class RecordAppOpenedUseCase(private val settings: SettingsRepository, private val clock: AppClock) {
    suspend operator fun invoke() = settings.update { it.copy(lastOpenedAt = clock.now()) }
}

data class PickedContactsResult(val added: Int, val updated: Int)

/**
 * Adds people chosen in the system contact picker (no address-book permission needed). They become real friends
 * (Close circle) tied to their contact, so a later full sync merges instead of duplicating.
 */
class ImportPickedContactsUseCase(private val friends: FriendRepository) {
    suspend operator fun invoke(records: List<ContactRecord>): PickedContactsResult {
        var added = 0
        var updated = 0
        records.filter { it.name.isNotBlank() }.distinctBy { it.contactId }.forEach { r ->
            val current = friends.friends.first()
            val match = current.firstOrNull { it.contactId == r.contactId }
                ?: current.firstOrNull { it.name.equals(r.name.trim(), ignoreCase = true) }
            if (match == null) {
                friends.upsert(
                    Friend(
                        id = FriendId("contact-${r.contactId}"),
                        name = r.name.trim(),
                        contactId = r.contactId,
                        phoneNumbers = r.phoneNumbers.distinct(),
                        birthday = r.birthday,
                        photo = r.photo,
                        isCloseFriend = true,
                        circle = Circle.CLOSE,
                    ),
                )
                added++
            } else {
                val merged = match.copy(
                    contactId = match.contactId ?: r.contactId,
                    phoneNumbers = (match.phoneNumbers + r.phoneNumbers).distinct(),
                    birthday = match.birthday ?: r.birthday,
                    photo = r.photo ?: match.photo,
                    isCloseFriend = true,
                    circle = match.circle ?: Circle.CLOSE,
                    isOrphaned = false,
                )
                if (merged != match) {
                    friends.upsert(merged)
                    updated++
                }
            }
        }
        return PickedContactsResult(added, updated)
    }
}

data class ContactsFileImportResult(val added: Int, val updated: Int, val skipped: Int)

/**
 * Imports a .vcf export (Google Contacts, iCloud, Android, Outlook). Matches existing friends by name or phone
 * and only fills gaps (phones, birthday, photo); new people become manual friends (not tied to the device address
 * book, so a later contacts sync never removes them). This is the address-book path for the web build.
 */
class ImportContactsFileUseCase(
    private val friends: FriendRepository,
    private val ids: IdGenerator,
    private val photos: PhotoShrinker = PhotoShrinker.None,
) {
    suspend operator fun invoke(raw: String): ContactsFileImportResult {
        val records = VCardParser.parse(raw)
        var added = 0
        var updated = 0
        var skipped = 0
        records.forEach { r ->
            val current = friends.friends.first()
            val phoneKeys = r.phoneNumbers.mapNotNull(::phoneKey).toSet()
            val match = current.firstOrNull { it.name.equals(r.name, ignoreCase = true) }
                ?: current.firstOrNull { f -> f.phoneNumbers.mapNotNull(::phoneKey).any { it in phoneKeys } }
            val photo = if (match?.photo == null) photos.photoOf(r.photo) else null
            if (match == null) {
                friends.upsert(Friend(FriendId("vcf-${ids.next()}"), r.name, phoneNumbers = r.phoneNumbers, birthday = r.birthday, photo = photo))
                added++
            } else {
                val merged = match.copy(
                    phoneNumbers = (match.phoneNumbers + r.phoneNumbers).distinctBy { phoneKey(it) ?: it },
                    birthday = match.birthday ?: r.birthday,
                    photo = match.photo ?: photo,
                )
                if (merged != match) {
                    friends.upsert(merged)
                    updated++
                } else {
                    skipped++
                }
            }
        }
        return ContactsFileImportResult(added, updated, skipped)
    }

    private fun phoneKey(raw: String): String? = raw.filter(Char::isDigit).takeLast(9).takeIf { it.length >= 6 }
}

/** A personalised birthday message drafted by the AI in the user's language. Requires an API key. */
class DraftBirthdayMessageUseCase(
    private val friends: FriendRepository,
    private val interactions: InteractionRepository,
    private val settings: SettingsRepository,
    private val assistant: AiAssistant,
    private val language: LanguageProvider,
) {
    suspend operator fun invoke(id: FriendId): Result<String> {
        val key = settings.settings.first().aiApiKey?.takeIf { it.isNotBlank() } ?: return Result.failure(MissingApiKeyException())
        val friend = friends.get(id) ?: return Result.failure(NoSuchElementException("friend"))
        return assistant.birthdayMessage(key, IdeaMapper.profile(friend, interactions.messages.first()), language.languageTag())
    }
}

/** AI gift ideas for a friend or one of their kids. Requires an API key. */
class SuggestGiftsUseCase(
    private val friends: FriendRepository,
    private val interactions: InteractionRepository,
    private val settings: SettingsRepository,
    private val assistant: AiAssistant,
    private val language: LanguageProvider,
) {
    suspend operator fun invoke(id: FriendId, kidId: String? = null): Result<List<String>> {
        val key = settings.settings.first().aiApiKey?.takeIf { it.isNotBlank() } ?: return Result.failure(MissingApiKeyException())
        val friend = friends.get(id) ?: return Result.failure(NoSuchElementException("friend"))
        val kid = friend.kids.firstOrNull { it.id == kidId }
        val alreadyHave = friend.gifts.filter { it.forKidId == kidId }.map { it.title.lowercase() }.toSet()
        return assistant.giftIdeas(
            key,
            IdeaMapper.profile(friend, interactions.messages.first()),
            kid?.name,
            kid?.likes.orEmpty(),
            language.languageTag(),
        )
            .map { ideas -> ideas.map(String::trim).filter { it.isNotEmpty() && it.lowercase() !in alreadyHave }.distinct() }
    }
}

class MarkIdeaProposedUseCase(private val ideas: IdeaRepository, private val clock: AppClock) {
    suspend operator fun invoke(id: String) = ideas.markProposed(id, clock.now())
}
