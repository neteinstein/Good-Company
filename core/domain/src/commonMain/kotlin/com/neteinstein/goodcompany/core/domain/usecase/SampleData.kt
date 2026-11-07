package com.neteinstein.goodcompany.core.domain.usecase

import com.neteinstein.goodcompany.core.domain.model.Birthday
import com.neteinstein.goodcompany.core.domain.model.Friend
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.Hangout
import com.neteinstein.goodcompany.core.domain.model.Idea
import com.neteinstein.goodcompany.core.domain.model.IdeaKind
import com.neteinstein.goodcompany.core.domain.model.ImportantEvent
import com.neteinstein.goodcompany.core.domain.model.Kid
import com.neteinstein.goodcompany.core.domain.model.Place
import com.neteinstein.goodcompany.core.domain.model.Reminder
import com.neteinstein.goodcompany.core.domain.repository.FriendRepository
import com.neteinstein.goodcompany.core.domain.repository.HangoutRepository
import com.neteinstein.goodcompany.core.domain.repository.IdeaRepository
import com.neteinstein.goodcompany.core.domain.repository.ReminderRepository
import com.neteinstein.goodcompany.core.domain.util.AppClock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.atTime
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

/**
 * A believable sample circle so people can explore the app before importing their own contacts
 * (and so screenshots/demos look real). Every id starts with [PREFIX], which is how it's removed again.
 * Dates are relative to "today" so the sample always has something coming up.
 */
object SampleData {
    const val PREFIX = "sample-"

    data class Bundle(val friends: List<Friend>, val hangouts: List<Hangout>, val reminders: List<Reminder>, val ideas: List<Idea>)

    fun build(clock: AppClock): Bundle {
        val today = clock.today()
        val now = clock.now()
        val zone = clock.timeZone
        fun id(s: String) = FriendId(PREFIX + s)
        fun bday(inDays: Int, age: Int): Birthday {
            val d = today.plus(inDays, DateTimeUnit.DAY)
            return Birthday(d.month.ordinal + 1, d.day, d.year - age)
        }
        fun day(offset: Int): LocalDate = if (offset >= 0) today.plus(offset, DateTimeUnit.DAY) else today.minus(-offset, DateTimeUnit.DAY)
        fun at(offset: Int, hour: Int) = day(offset).atTime(LocalTime(hour, 0)).toInstant(zone)

        val friends = listOf(
            Friend(
                id = id("ana"), name = "Ana Silva", isCloseFriend = true, notifyBirthday = true, suggestEvents = true,
                phoneNumbers = listOf("+351 912 345 678"), birthday = bday(3, 34), meetEveryDays = 14,
                likes = listOf("Jazz", "Sushi", "Hiking", "Murakami"), dislikes = listOf("Crowds", "Cilantro"),
                kids = listOf(Kid(PREFIX + "leo", "Leo", bday(9, 6), listOf("Dinosaurs", "LEGO", "Pancakes"))),
                importantEvents = listOf(ImportantEvent(PREFIX + "ana-knee", "Knee surgery", day(2), remindDaysBefore = 1)),
                notes = "Partner: Tiago. Allergic to peanuts.",
            ),
            Friend(
                id = id("rui"), name = "Rui Costa", isCloseFriend = true, notifyBirthday = true, suggestEvents = true,
                phoneNumbers = listOf("+351 913 222 111"), birthday = bday(19, 37), meetEveryDays = 30,
                likes = listOf("Formula 1", "Craft beer", "Board games"), dislikes = listOf("Early mornings"),
            ),
            Friend(
                id = id("marta"), name = "Marta Lopes", isCloseFriend = true, notifyBirthday = true,
                phoneNumbers = listOf("+351 914 555 000"), birthday = bday(41, 31),
                likes = listOf("Yoga", "Indie films", "Ceramics"), dislikes = listOf("Horror movies"),
                kids = listOf(
                    Kid(PREFIX + "ines", "Inês", bday(25, 4), listOf("Frozen", "Painting")),
                    Kid(PREFIX + "tomas", "Tomás", bday(130, 8), listOf("Football", "Minecraft")),
                ),
                importantEvents = listOf(ImportantEvent(PREFIX + "marta-job", "Job interview", day(1))),
            ),
            Friend(
                id = id("pedro"),
                name = "Pedro Almeida",
                isCloseFriend = true,
                meetEveryDays = 7,
                phoneNumbers = listOf("+351 915 777 888"),
                birthday = bday(88, 35),
                likes = listOf("Football", "BBQ", "Surfing"),
            ),
            Friend(
                id = id("sofia"), name = "Sofia Martins", isCloseFriend = true, notifyBirthday = true, suggestEvents = true,
                phoneNumbers = listOf("+351 916 123 456"), birthday = bday(0, 29), meetEveryDays = 21,
                likes = listOf("Photography", "Travel", "Specialty coffee"), dislikes = listOf("Spicy food"),
            ),
            Friend(
                id = id("beatriz"),
                name = "Beatriz Rocha",
                isCloseFriend = true,
                birthday = bday(57, 40),
                likes = listOf("Theatre", "Cooking", "Wine"),
                kids = listOf(Kid(PREFIX + "clara", "Clara", bday(12, 10), listOf("Harry Potter", "Ballet"))),
            ),
            Friend(id = id("joao"), name = "João Pereira", contactId = PREFIX + "c-joao", phoneNumbers = listOf("+351 917 000 111")),
            Friend(id = id("miguel"), name = "Miguel Santos", contactId = PREFIX + "c-miguel"),
            Friend(id = id("carla"), name = "Carla Nunes", contactId = PREFIX + "c-carla", likes = listOf("Running")),
        )

        fun hangout(key: String, offset: Int, hour: Int, place: String?, vararg who: String, note: String = "") =
            Hangout(PREFIX + key, who.map(::id), at(offset, hour), place?.let { Place(it) }, note)
        val hangouts = listOf(
            hangout("h1", -2, 20, "Hot Clube de Portugal", "ana", "rui", note = "Jazz night 🎷"),
            hangout("h2", -5, 13, "Time Out Market", "sofia"),
            hangout("h3", -9, 19, "Rui's place", "rui", "pedro", "beatriz", note = "Board games"),
            hangout("h4", -16, 10, "Parque Eduardo VII", "marta"),
            hangout("h5", -18, 21, "Cinema Ideal", "ana", "marta"),
            hangout("h6", -26, 12, "Café Tati", "pedro"),
            hangout("h7", -33, 20, "Beatriz's dinner party", "beatriz", "ana", "rui", "sofia"),
            hangout("h8", -47, 9, "Guincho beach", "pedro", "rui", note = "Surf morning"),
        )

        val reminders = listOf(
            Reminder(PREFIX + "r1", "Book tickets for the jazz festival", listOf(id("ana"), id("rui")), at(2, 18), createdAt = now - 3.days),
            Reminder(PREFIX + "r2", "Buy Leo's birthday present", listOf(id("ana")), at(6, 12), createdAt = now - 1.days),
            Reminder(PREFIX + "r3", "Ask Marta how the interview went", listOf(id("marta")), at(1, 19), createdAt = now - 2.hours),
            Reminder(PREFIX + "r4", "Plan the surf weekend", listOf(id("pedro"), id("rui")), null, createdAt = now - 10.days),
            Reminder(PREFIX + "r5", "Send Sofia the trip photos", listOf(id("sofia")), at(-1, 10), done = true, createdAt = now - 6.days),
        )

        val ideas = listOf(
            Idea(
                PREFIX + "i1",
                listOf(id("ana"), id("rui")),
                IdeaKind.EVENT,
                "Lisbon Jazz Festival — late show",
                "Ana loves jazz and Rui's into anything live. The Friday late set is small and seated — no crowds.",
                "https://example.com/jazz",
                "Fri, 22:00",
                now - 1.hours,
            ),
            Idea(
                PREFIX + "i2",
                listOf(id("sofia")),
                IdeaKind.EVENT,
                "Street photography walk in Alfama",
                "A guided golden-hour walk — perfect for Sofia's new camera, and it ends at a specialty coffee bar.",
                null,
                "Sat, 17:30",
                now - 1.hours,
            ),
            Idea(
                PREFIX + "i3",
                listOf(id("rui")),
                IdeaKind.NEWS,
                "New F1 street circuit announced",
                "Big news for Rui — bring it up next time; he's been talking about going to a race abroad.",
                null,
                null,
                now - 2.hours,
            ),
            Idea(
                PREFIX + "i4",
                listOf(id("marta")),
                IdeaKind.TALKING_POINT,
                "Ceramics studio she mentioned",
                "Marta said she wanted to try wheel throwing again — ask whether she found a studio.",
                null,
                null,
                now - 3.hours,
            ),
        )
        return Bundle(friends, hangouts, reminders, ideas)
    }

    fun isSample(id: String) = id.startsWith(PREFIX)
}

/** Adds the sample circle (idempotent). */
class LoadSampleDataUseCase(
    private val friends: FriendRepository,
    private val hangouts: HangoutRepository,
    private val reminders: ReminderRepository,
    private val ideas: IdeaRepository,
    private val clock: AppClock,
) {
    suspend operator fun invoke() {
        val bundle = SampleData.build(clock)
        bundle.friends.forEach { friends.upsert(it) }
        val existingHangouts = hangouts.hangouts.first().map { it.id }.toSet()
        bundle.hangouts.filterNot { it.id in existingHangouts }.forEach { hangouts.add(it) }
        bundle.reminders.forEach { reminders.upsert(it) }
        val existingIdeas = ideas.ideas.first().map { it.id }.toSet()
        ideas.addAll(bundle.ideas.filterNot { it.id in existingIdeas })
    }
}

/** Removes everything the sample added, leaving the user's own data untouched. */
class RemoveSampleDataUseCase(
    private val friends: FriendRepository,
    private val hangouts: HangoutRepository,
    private val reminders: ReminderRepository,
    private val ideas: IdeaRepository,
) {
    suspend operator fun invoke() {
        friends.friends.first().filter { SampleData.isSample(it.id.value) }.forEach { friends.delete(it.id) }
        hangouts.hangouts.first().filter { SampleData.isSample(it.id) }.forEach { hangouts.delete(it.id) }
        reminders.reminders.first().filter { SampleData.isSample(it.id) }.forEach { reminders.delete(it.id) }
        ideas.ideas.first().filter { SampleData.isSample(it.id) && !it.dismissed }.forEach { ideas.dismiss(it.id) }
    }
}

/** True when the sample circle is currently loaded. */
class HasSampleDataUseCase(private val friends: FriendRepository) {
    operator fun invoke(): Flow<Boolean> = friends.friends.map { list -> list.any { SampleData.isSample(it.id.value) } }
}
