package com.neteinstein.goodcompany.core.domain.usecase

import com.neteinstein.goodcompany.core.domain.model.Birthday
import com.neteinstein.goodcompany.core.domain.model.Circle
import com.neteinstein.goodcompany.core.domain.model.Friend
import com.neteinstein.goodcompany.core.domain.model.FriendGroup
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.Gift
import com.neteinstein.goodcompany.core.domain.model.GiftOccasion
import com.neteinstein.goodcompany.core.domain.model.Hangout
import com.neteinstein.goodcompany.core.domain.model.Idea
import com.neteinstein.goodcompany.core.domain.model.IdeaKind
import com.neteinstein.goodcompany.core.domain.model.ImportantEvent
import com.neteinstein.goodcompany.core.domain.model.Kid
import com.neteinstein.goodcompany.core.domain.model.LifeUpdate
import com.neteinstein.goodcompany.core.domain.model.Occasion
import com.neteinstein.goodcompany.core.domain.model.Place
import com.neteinstein.goodcompany.core.domain.model.Plan
import com.neteinstein.goodcompany.core.domain.model.Reminder
import com.neteinstein.goodcompany.core.domain.repository.FriendRepository
import com.neteinstein.goodcompany.core.domain.repository.GroupRepository
import com.neteinstein.goodcompany.core.domain.repository.HangoutRepository
import com.neteinstein.goodcompany.core.domain.repository.IdeaRepository
import com.neteinstein.goodcompany.core.domain.repository.PlanRepository
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
 * Every human-readable string in the sample circle, so the UI can provide a translated copy
 * (see `SampleTextsProvider`). Names and place names are proper nouns and stay as they are.
 */
data class SampleTexts(
    val anaNotes: String = "Partner: Tiago. Allergic to peanuts.",
    val kneeSurgery: String = "Knee surgery",
    val jobInterview: String = "Job interview",
    val jazzNight: String = "Jazz night 🎷",
    val boardGames: String = "Board games",
    val surfMorning: String = "Surf morning",
    val ruisPlace: String = "Rui's place",
    val beatrizDinner: String = "Beatriz's dinner party",
    val reminderTickets: String = "Book tickets for the jazz festival",
    val reminderPresent: String = "Buy Leo's birthday present",
    val reminderAskMarta: String = "Ask Marta how the interview went",
    val reminderSurfWeekend: String = "Plan the surf weekend",
    val reminderPhotos: String = "Send Sofia the trip photos",
    val ideaJazzTitle: String = "Lisbon Jazz Festival — late show",
    val ideaJazzBody: String = "Ana loves jazz and Rui's into anything live. The Friday late set is small and seated — no crowds.",
    val ideaJazzWhen: String = "Fri, 22:00",
    val ideaPhotoTitle: String = "Street photography walk in Alfama",
    val ideaPhotoBody: String = "A guided golden-hour walk — perfect for Sofia's new camera, and it ends at a specialty coffee bar.",
    val ideaPhotoWhen: String = "Sat, 17:30",
    val ideaF1Title: String = "New F1 street circuit announced",
    val ideaF1Body: String = "Big news for Rui — bring it up next time; he's been talking about going to a race abroad.",
    val ideaCeramicsTitle: String = "Ceramics studio she mentioned",
    val ideaCeramicsBody: String = "Marta said she wanted to try wheel throwing again — ask whether she found a studio.",
    val giftVinyl: String = "Kind of Blue on vinyl",
    val giftMurakami: String = "Murakami first edition",
    val giftDinoLego: String = "LEGO dinosaur set",
    val giftBoardGame: String = "Wingspan board game",
    val giftCeramicsClass: String = "Ceramics class voucher",
    val groupBoardGames: String = "Board game night",
    val groupSurf: String = "Surf crew",
    // Tastes
    val jazz: String = "Jazz",
    val sushi: String = "Sushi",
    val hiking: String = "Hiking",
    val crowds: String = "Crowds",
    val cilantro: String = "Cilantro",
    val dinosaurs: String = "Dinosaurs",
    val pancakes: String = "Pancakes",
    val formula1: String = "Formula 1",
    val craftBeer: String = "Craft beer",
    val boardGamesTaste: String = "Board games",
    val earlyMornings: String = "Early mornings",
    val yoga: String = "Yoga",
    val indieFilms: String = "Indie films",
    val ceramics: String = "Ceramics",
    val horrorMovies: String = "Horror movies",
    val painting: String = "Painting",
    val football: String = "Football",
    val bbq: String = "BBQ",
    val surfing: String = "Surfing",
    val photography: String = "Photography",
    val travel: String = "Travel",
    val specialtyCoffee: String = "Specialty coffee",
    val spicyFood: String = "Spicy food",
    val theatre: String = "Theatre",
    val cooking: String = "Cooking",
    val wine: String = "Wine",
    val ballet: String = "Ballet",
    val running: String = "Running",
    // What's new (life updates) and a planned meetup, for the catch-up story
    val newsLeoSchool: String = "Leo just started first grade",
    val newsRuiMove: String = "Moved to a new flat in Alvalade",
    val newsMartaJob: String = "Looking for a new job, tired of agency life",
    val planLunch: String = "Lunch at Time Out Market",
)

/** Supplies [SampleTexts] in the user's language (implemented with UI string resources). */
fun interface SampleTextsProvider {
    suspend fun texts(): SampleTexts
}

/**
 * A believable sample circle so people can explore the app before importing their own contacts
 * (and so screenshots/demos look real). Every id starts with [PREFIX], which is how it's removed again.
 * Dates are relative to "today" so the sample always has something coming up.
 */
object SampleData {
    const val PREFIX = "sample-"

    data class Bundle(
        val friends: List<Friend>,
        val hangouts: List<Hangout>,
        val reminders: List<Reminder>,
        val ideas: List<Idea>,
        val groups: List<FriendGroup> = emptyList(),
        val plans: List<Plan> = emptyList(),
    )

    fun build(clock: AppClock, t: SampleTexts = SampleTexts()): Bundle {
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
                id = id("ana"), name = "Ana Silva", isCloseFriend = true, circle = Circle.CLOSE, notifyBirthday = true, suggestEvents = true,
                phoneNumbers = listOf("+351 912 345 678"), birthday = bday(3, 34), meetEveryDays = 14,
                likes = listOf(t.jazz, t.sushi, t.hiking, "Murakami"), dislikes = listOf(t.crowds, t.cilantro),
                likesAddedAt = mapOf(t.hiking to now - 1.days),
                kids = listOf(Kid(PREFIX + "leo", "Leo", bday(9, 6), listOf(t.dinosaurs, "LEGO", t.pancakes))),
                importantEvents = listOf(ImportantEvent(PREFIX + "ana-knee", t.kneeSurgery, day(2), remindDaysBefore = 1, addedAt = now - 1.days)),
                lifeUpdates = listOf(LifeUpdate(PREFIX + "news-leo", t.newsLeoSchool, now - 1.days)),
                notes = t.anaNotes,
                occasions = listOf(Occasion.WEEKDAY_LUNCH, Occasion.OUTDOORS, Occasion.CULTURE),
                gifts = listOf(
                    Gift(PREFIX + "g-vinyl", t.giftVinyl),
                    Gift(PREFIX + "g-dino", t.giftDinoLego, forKidId = PREFIX + "leo"),
                    Gift(PREFIX + "g-murakami", t.giftMurakami, givenOn = day(-362), occasion = GiftOccasion.BIRTHDAY),
                ),
            ),
            Friend(
                id = id("rui"), name = "Rui Costa", isCloseFriend = true, circle = Circle.CLOSE, notifyBirthday = true, suggestEvents = true,
                phoneNumbers = listOf("+351 913 222 111"), birthday = bday(19, 37),
                likes = listOf(t.formula1, t.craftBeer, t.boardGamesTaste), dislikes = listOf(t.earlyMornings),
                lifeUpdates = listOf(LifeUpdate(PREFIX + "news-rui", t.newsRuiMove, now - 3.days)),
                occasions = listOf(Occasion.DRINKS, Occasion.SPORTS),
                gifts = listOf(Gift(PREFIX + "g-wingspan", t.giftBoardGame, givenOn = day(-320), occasion = GiftOccasion.CHRISTMAS)),
            ),
            Friend(
                id = id("marta"), name = "Marta Lopes", isCloseFriend = true, circle = Circle.CLOSE, notifyBirthday = true,
                phoneNumbers = listOf("+351 914 555 000"), birthday = bday(41, 31),
                likes = listOf(t.yoga, t.indieFilms, t.ceramics), dislikes = listOf(t.horrorMovies),
                kids = listOf(
                    Kid(PREFIX + "ines", "Inês", bday(25, 4), listOf("Frozen", t.painting)),
                    Kid(PREFIX + "tomas", "Tomás", bday(130, 8), listOf(t.football, "Minecraft")),
                ),
                importantEvents = listOf(ImportantEvent(PREFIX + "marta-job", t.jobInterview, day(1))),
                lifeUpdates = listOf(LifeUpdate(PREFIX + "news-marta", t.newsMartaJob, now - 20.days)),
                gifts = listOf(Gift(PREFIX + "g-ceramics", t.giftCeramicsClass)),
            ),
            Friend(
                id = id("pedro"),
                name = "Pedro Almeida",
                isCloseFriend = true,
                circle = Circle.INNER,
                phoneNumbers = listOf("+351 915 777 888"),
                birthday = bday(88, 35),
                likes = listOf(t.football, t.bbq, t.surfing),
                occasions = listOf(Occasion.WEEKDAY_LUNCH, Occasion.OUTDOORS, Occasion.SPORTS),
            ),
            Friend(
                id = id("sofia"), name = "Sofia Martins", isCloseFriend = true, circle = Circle.INNER, notifyBirthday = true, suggestEvents = true,
                phoneNumbers = listOf("+351 916 123 456"), birthday = bday(0, 29), meetEveryDays = 21,
                likes = listOf(t.photography, t.travel, t.specialtyCoffee), dislikes = listOf(t.spicyFood),
                occasions = listOf(Occasion.COFFEE, Occasion.WEEKDAY_LUNCH),
            ),
            Friend(
                id = id("beatriz"),
                name = "Beatriz Rocha",
                isCloseFriend = true,
                circle = Circle.GOOD,
                birthday = bday(57, 40),
                likes = listOf(t.theatre, t.cooking, t.wine),
                kids = listOf(Kid(PREFIX + "clara", "Clara", bday(12, 10), listOf("Harry Potter", t.ballet))),
            ),
            Friend(id = id("joao"), name = "João Pereira", contactId = PREFIX + "c-joao", phoneNumbers = listOf("+351 917 000 111")),
            Friend(id = id("miguel"), name = "Miguel Santos", contactId = PREFIX + "c-miguel"),
            Friend(id = id("carla"), name = "Carla Nunes", contactId = PREFIX + "c-carla", likes = listOf(t.running)),
        )

        fun hangout(key: String, offset: Int, hour: Int, place: String?, vararg who: String, note: String = "") =
            Hangout(PREFIX + key, who.map(::id), at(offset, hour), place?.let { Place(it) }, note)
        val hangouts = listOf(
            hangout("h1", -2, 20, "Hot Clube de Portugal", "ana", "rui", note = t.jazzNight),
            hangout("h2", -5, 13, "Time Out Market", "sofia"),
            hangout("h3", -9, 19, t.ruisPlace, "rui", "pedro", "beatriz", note = t.boardGames),
            hangout("h4", -16, 10, "Parque Eduardo VII", "marta"),
            hangout("h5", -18, 21, "Cinema Ideal", "ana", "marta"),
            hangout("h6", -26, 12, "Café Tati", "pedro"),
            hangout("h7", -33, 20, t.beatrizDinner, "beatriz", "ana", "rui", "sofia"),
            hangout("h8", -47, 9, "Guincho", "pedro", "rui", note = t.surfMorning),
            hangout("h9", -75, 21, "Hot Clube de Portugal", "ana", "sofia"),
            hangout("h10", -120, 19, t.ruisPlace, "rui", "pedro", "beatriz"),
            hangout("h11", -150, 13, "Time Out Market", "marta", "ana"),
        )

        val reminders = listOf(
            Reminder(PREFIX + "r1", t.reminderTickets, listOf(id("ana"), id("rui")), at(2, 18), createdAt = now - 3.days),
            Reminder(PREFIX + "r2", t.reminderPresent, listOf(id("ana")), at(6, 12), createdAt = now - 1.days),
            Reminder(PREFIX + "r3", t.reminderAskMarta, listOf(id("marta")), at(1, 19), createdAt = now - 2.hours),
            Reminder(PREFIX + "r4", t.reminderSurfWeekend, listOf(id("pedro"), id("rui")), null, createdAt = now - 10.days),
            Reminder(PREFIX + "r5", t.reminderPhotos, listOf(id("sofia")), at(-1, 10), done = true, createdAt = now - 6.days),
        )

        val ideas = listOf(
            Idea(
                PREFIX + "i1",
                listOf(id("ana"), id("rui")),
                IdeaKind.EVENT,
                t.ideaJazzTitle,
                t.ideaJazzBody,
                "https://example.com/jazz",
                t.ideaJazzWhen,
                now - 1.hours,
            ),
            Idea(PREFIX + "i2", listOf(id("sofia")), IdeaKind.EVENT, t.ideaPhotoTitle, t.ideaPhotoBody, null, t.ideaPhotoWhen, now - 1.hours),
            Idea(PREFIX + "i3", listOf(id("rui")), IdeaKind.NEWS, t.ideaF1Title, t.ideaF1Body, null, null, now - 2.hours),
            Idea(PREFIX + "i4", listOf(id("marta")), IdeaKind.TALKING_POINT, t.ideaCeramicsTitle, t.ideaCeramicsBody, null, null, now - 3.hours),
        )

        val groups = listOf(
            FriendGroup(PREFIX + "grp-games", t.groupBoardGames, "🎲", listOf(id("rui"), id("pedro"), id("beatriz")), meetEveryDays = 30),
            FriendGroup(PREFIX + "grp-surf", t.groupSurf, "🏄", listOf(id("pedro"), id("rui")), meetEveryDays = 21),
        )
        val plans = listOf(
            Plan(PREFIX + "plan-lunch", listOf(id("ana")), t.planLunch, day(0), place = "Time Out Market", createdAt = now - 2.days),
        )
        return Bundle(friends, hangouts, reminders, ideas, groups, plans)
    }

    fun isSample(id: String) = id.startsWith(PREFIX)
}

/** Adds the sample circle (idempotent), in the user's language. */
class LoadSampleDataUseCase(
    private val friends: FriendRepository,
    private val hangouts: HangoutRepository,
    private val reminders: ReminderRepository,
    private val ideas: IdeaRepository,
    private val groups: GroupRepository,
    private val plans: PlanRepository,
    private val clock: AppClock,
    private val texts: SampleTextsProvider = SampleTextsProvider { SampleTexts() },
) {
    suspend operator fun invoke() {
        val bundle = SampleData.build(clock, texts.texts())
        bundle.friends.forEach { friends.upsert(it) }
        val existingHangouts = hangouts.hangouts.first().map { it.id }.toSet()
        bundle.hangouts.filterNot { it.id in existingHangouts }.forEach { hangouts.add(it) }
        bundle.reminders.forEach { reminders.upsert(it) }
        val existingIdeas = ideas.ideas.first().map { it.id }.toSet()
        ideas.addAll(bundle.ideas.filterNot { it.id in existingIdeas })
        bundle.groups.forEach { groups.upsert(it) }
        val existingPlans = plans.plans.first().map { it.id }.toSet()
        bundle.plans.filterNot { it.id in existingPlans }.forEach { plans.upsert(it) }
    }
}

/** Removes everything the sample added, leaving the user's own data untouched. */
class RemoveSampleDataUseCase(
    private val friends: FriendRepository,
    private val hangouts: HangoutRepository,
    private val reminders: ReminderRepository,
    private val ideas: IdeaRepository,
    private val groups: GroupRepository,
    private val plans: PlanRepository,
) {
    suspend operator fun invoke() {
        friends.friends.first().filter { SampleData.isSample(it.id.value) }.forEach { friends.delete(it.id) }
        hangouts.hangouts.first().filter { SampleData.isSample(it.id) }.forEach { hangouts.delete(it.id) }
        reminders.reminders.first().filter { SampleData.isSample(it.id) }.forEach { reminders.delete(it.id) }
        ideas.ideas.first().filter { SampleData.isSample(it.id) && !it.dismissed }.forEach { ideas.dismiss(it.id) }
        groups.groups.first().filter { SampleData.isSample(it.id) }.forEach { groups.delete(it.id) }
        plans.plans.first().filter { SampleData.isSample(it.id) }.forEach { plans.delete(it.id) }
    }
}

/** True when the sample circle is currently loaded. */
class HasSampleDataUseCase(private val friends: FriendRepository) {
    operator fun invoke(): Flow<Boolean> = friends.friends.map { list -> list.any { SampleData.isSample(it.id.value) } }
}
