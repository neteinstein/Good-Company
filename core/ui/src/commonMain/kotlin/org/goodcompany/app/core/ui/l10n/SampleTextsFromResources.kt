package org.goodcompany.app.core.ui.l10n

import org.goodcompany.app.core.domain.usecase.SampleTexts
import org.goodcompany.app.core.domain.usecase.SampleTextsProvider
import org.goodcompany.app.core.ui.resources.Res
import org.goodcompany.app.core.ui.resources.sample_ana_job
import org.goodcompany.app.core.ui.resources.sample_ana_met_place
import org.goodcompany.app.core.ui.resources.sample_ana_notes
import org.goodcompany.app.core.ui.resources.sample_beagle
import org.goodcompany.app.core.ui.resources.sample_beatriz_dinner
import org.goodcompany.app.core.ui.resources.sample_board_games_note
import org.goodcompany.app.core.ui.resources.sample_circle_board_games
import org.goodcompany.app.core.ui.resources.sample_circle_choir
import org.goodcompany.app.core.ui.resources.sample_circle_surf
import org.goodcompany.app.core.ui.resources.sample_field_football_club
import org.goodcompany.app.core.ui.resources.sample_field_shoe_size
import org.goodcompany.app.core.ui.resources.sample_gift_board_game
import org.goodcompany.app.core.ui.resources.sample_gift_ceramics
import org.goodcompany.app.core.ui.resources.sample_gift_dino_lego
import org.goodcompany.app.core.ui.resources.sample_gift_list_christmas
import org.goodcompany.app.core.ui.resources.sample_gift_murakami
import org.goodcompany.app.core.ui.resources.sample_gift_vinyl
import org.goodcompany.app.core.ui.resources.sample_idea_ceramics_body
import org.goodcompany.app.core.ui.resources.sample_idea_ceramics_title
import org.goodcompany.app.core.ui.resources.sample_idea_f1_body
import org.goodcompany.app.core.ui.resources.sample_idea_f1_title
import org.goodcompany.app.core.ui.resources.sample_idea_jazz_body
import org.goodcompany.app.core.ui.resources.sample_idea_jazz_title
import org.goodcompany.app.core.ui.resources.sample_idea_jazz_when
import org.goodcompany.app.core.ui.resources.sample_idea_photo_body
import org.goodcompany.app.core.ui.resources.sample_idea_photo_title
import org.goodcompany.app.core.ui.resources.sample_idea_photo_when
import org.goodcompany.app.core.ui.resources.sample_jazz_night
import org.goodcompany.app.core.ui.resources.sample_job_interview
import org.goodcompany.app.core.ui.resources.sample_knee_surgery
import org.goodcompany.app.core.ui.resources.sample_marta_job
import org.goodcompany.app.core.ui.resources.sample_news_leo_school
import org.goodcompany.app.core.ui.resources.sample_news_marta_job
import org.goodcompany.app.core.ui.resources.sample_news_rui_move
import org.goodcompany.app.core.ui.resources.sample_plan_lunch
import org.goodcompany.app.core.ui.resources.sample_reminder_ask_marta
import org.goodcompany.app.core.ui.resources.sample_reminder_photos
import org.goodcompany.app.core.ui.resources.sample_reminder_present
import org.goodcompany.app.core.ui.resources.sample_reminder_surf
import org.goodcompany.app.core.ui.resources.sample_reminder_tickets
import org.goodcompany.app.core.ui.resources.sample_rui_job
import org.goodcompany.app.core.ui.resources.sample_ruis_place
import org.goodcompany.app.core.ui.resources.sample_surf_morning
import org.goodcompany.app.core.ui.resources.sample_taste_ballet
import org.goodcompany.app.core.ui.resources.sample_taste_bbq
import org.goodcompany.app.core.ui.resources.sample_taste_board_games
import org.goodcompany.app.core.ui.resources.sample_taste_ceramics
import org.goodcompany.app.core.ui.resources.sample_taste_cilantro
import org.goodcompany.app.core.ui.resources.sample_taste_coffee
import org.goodcompany.app.core.ui.resources.sample_taste_cooking
import org.goodcompany.app.core.ui.resources.sample_taste_craft_beer
import org.goodcompany.app.core.ui.resources.sample_taste_crowds
import org.goodcompany.app.core.ui.resources.sample_taste_dinosaurs
import org.goodcompany.app.core.ui.resources.sample_taste_early_mornings
import org.goodcompany.app.core.ui.resources.sample_taste_football
import org.goodcompany.app.core.ui.resources.sample_taste_formula1
import org.goodcompany.app.core.ui.resources.sample_taste_hiking
import org.goodcompany.app.core.ui.resources.sample_taste_horror
import org.goodcompany.app.core.ui.resources.sample_taste_indie_films
import org.goodcompany.app.core.ui.resources.sample_taste_jazz
import org.goodcompany.app.core.ui.resources.sample_taste_oat_flat_white
import org.goodcompany.app.core.ui.resources.sample_taste_padel
import org.goodcompany.app.core.ui.resources.sample_taste_painting
import org.goodcompany.app.core.ui.resources.sample_taste_pancakes
import org.goodcompany.app.core.ui.resources.sample_taste_photography
import org.goodcompany.app.core.ui.resources.sample_taste_running
import org.goodcompany.app.core.ui.resources.sample_taste_spicy
import org.goodcompany.app.core.ui.resources.sample_taste_surfing
import org.goodcompany.app.core.ui.resources.sample_taste_sushi
import org.goodcompany.app.core.ui.resources.sample_taste_theatre
import org.goodcompany.app.core.ui.resources.sample_taste_trail_running
import org.goodcompany.app.core.ui.resources.sample_taste_travel
import org.goodcompany.app.core.ui.resources.sample_taste_vegetarian_weekdays
import org.goodcompany.app.core.ui.resources.sample_taste_wine
import org.goodcompany.app.core.ui.resources.sample_taste_yoga
import org.goodcompany.app.core.ui.resources.sample_tl_email_flats
import org.goodcompany.app.core.ui.resources.sample_tl_journal_ana
import org.goodcompany.app.core.ui.resources.sample_tl_journal_rui
import org.goodcompany.app.core.ui.resources.sample_tl_message_surf
import org.goodcompany.app.core.ui.resources.sample_tl_video_geres
import org.jetbrains.compose.resources.getString

/** The sample circle in the user's language (generated mapping: one resource per [SampleTexts] field). */
object SampleTextsFromResources : SampleTextsProvider {
    override suspend fun texts(): SampleTexts = base().withTimeline()

    // Split in two: one suspend function with every string is too large for the JVM.
    private suspend fun base(): SampleTexts = SampleTexts(
        anaNotes = getString(Res.string.sample_ana_notes),
        kneeSurgery = getString(Res.string.sample_knee_surgery),
        jobInterview = getString(Res.string.sample_job_interview),
        jazzNight = getString(Res.string.sample_jazz_night),
        boardGames = getString(Res.string.sample_board_games_note),
        surfMorning = getString(Res.string.sample_surf_morning),
        ruisPlace = getString(Res.string.sample_ruis_place),
        beatrizDinner = getString(Res.string.sample_beatriz_dinner),
        reminderTickets = getString(Res.string.sample_reminder_tickets),
        reminderPresent = getString(Res.string.sample_reminder_present),
        reminderAskMarta = getString(Res.string.sample_reminder_ask_marta),
        reminderSurfWeekend = getString(Res.string.sample_reminder_surf),
        reminderPhotos = getString(Res.string.sample_reminder_photos),
        ideaJazzTitle = getString(Res.string.sample_idea_jazz_title),
        ideaJazzBody = getString(Res.string.sample_idea_jazz_body),
        ideaJazzWhen = getString(Res.string.sample_idea_jazz_when),
        ideaPhotoTitle = getString(Res.string.sample_idea_photo_title),
        ideaPhotoBody = getString(Res.string.sample_idea_photo_body),
        ideaPhotoWhen = getString(Res.string.sample_idea_photo_when),
        ideaF1Title = getString(Res.string.sample_idea_f1_title),
        ideaF1Body = getString(Res.string.sample_idea_f1_body),
        ideaCeramicsTitle = getString(Res.string.sample_idea_ceramics_title),
        ideaCeramicsBody = getString(Res.string.sample_idea_ceramics_body),
        giftVinyl = getString(Res.string.sample_gift_vinyl),
        giftMurakami = getString(Res.string.sample_gift_murakami),
        giftDinoLego = getString(Res.string.sample_gift_dino_lego),
        giftBoardGame = getString(Res.string.sample_gift_board_game),
        giftCeramicsClass = getString(Res.string.sample_gift_ceramics),
        giftListChristmas = getString(Res.string.sample_gift_list_christmas),
        groupBoardGames = getString(Res.string.sample_circle_board_games),
        groupSurf = getString(Res.string.sample_circle_surf),
        groupChoir = getString(Res.string.sample_circle_choir),
        jazz = getString(Res.string.sample_taste_jazz),
        sushi = getString(Res.string.sample_taste_sushi),
        hiking = getString(Res.string.sample_taste_hiking),
        crowds = getString(Res.string.sample_taste_crowds),
        cilantro = getString(Res.string.sample_taste_cilantro),
        dinosaurs = getString(Res.string.sample_taste_dinosaurs),
        pancakes = getString(Res.string.sample_taste_pancakes),
        formula1 = getString(Res.string.sample_taste_formula1),
        craftBeer = getString(Res.string.sample_taste_craft_beer),
        boardGamesTaste = getString(Res.string.sample_taste_board_games),
        earlyMornings = getString(Res.string.sample_taste_early_mornings),
        yoga = getString(Res.string.sample_taste_yoga),
        indieFilms = getString(Res.string.sample_taste_indie_films),
        ceramics = getString(Res.string.sample_taste_ceramics),
        horrorMovies = getString(Res.string.sample_taste_horror),
        painting = getString(Res.string.sample_taste_painting),
        football = getString(Res.string.sample_taste_football),
        bbq = getString(Res.string.sample_taste_bbq),
        surfing = getString(Res.string.sample_taste_surfing),
        photography = getString(Res.string.sample_taste_photography),
        travel = getString(Res.string.sample_taste_travel),
        specialtyCoffee = getString(Res.string.sample_taste_coffee),
        spicyFood = getString(Res.string.sample_taste_spicy),
        theatre = getString(Res.string.sample_taste_theatre),
        cooking = getString(Res.string.sample_taste_cooking),
        wine = getString(Res.string.sample_taste_wine),
        ballet = getString(Res.string.sample_taste_ballet),
        running = getString(Res.string.sample_taste_running),
        newsLeoSchool = getString(Res.string.sample_news_leo_school),
        newsRuiMove = getString(Res.string.sample_news_rui_move),
        newsMartaJob = getString(Res.string.sample_news_marta_job),
        planLunch = getString(Res.string.sample_plan_lunch),
        anaJob = getString(Res.string.sample_ana_job),
        anaMetPlace = getString(Res.string.sample_ana_met_place),
        ruiJob = getString(Res.string.sample_rui_job),
        martaJob = getString(Res.string.sample_marta_job),
        beagle = getString(Res.string.sample_beagle),
        padel = getString(Res.string.sample_taste_padel),
        vegetarianWeekdays = getString(Res.string.sample_taste_vegetarian_weekdays),
        oatFlatWhite = getString(Res.string.sample_taste_oat_flat_white),
        trailRunning = getString(Res.string.sample_taste_trail_running),
        shoeSize = getString(Res.string.sample_field_shoe_size),
        footballClub = getString(Res.string.sample_field_football_club),
    )

    private suspend fun SampleTexts.withTimeline(): SampleTexts = copy(
        videoCallGeres = getString(Res.string.sample_tl_video_geres),
        emailFlatListings = getString(Res.string.sample_tl_email_flats),
        messageSurfPhotos = getString(Res.string.sample_tl_message_surf),
        journalAnaPorto = getString(Res.string.sample_tl_journal_ana),
        journalRuiRace = getString(Res.string.sample_tl_journal_rui),
    )
}
