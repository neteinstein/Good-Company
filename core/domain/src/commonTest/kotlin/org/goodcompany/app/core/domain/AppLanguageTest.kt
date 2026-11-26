package org.goodcompany.app.core.domain

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.goodcompany.app.core.domain.model.AppLanguage
import org.goodcompany.app.core.domain.model.AppSettings
import org.goodcompany.app.core.domain.usecase.withoutDeviceOnly
import org.goodcompany.app.core.testing.TestGraph
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Issue #174: choose the app's language, or follow the system. */
class AppLanguageTest {
    @Test
    fun theDefaultIsTheSystemLanguage_andABackupNeverCarriesTheChoice() {
        assertEquals(AppLanguage.SYSTEM, AppSettings().language)
        assertEquals(AppLanguage.SYSTEM, AppSettings(language = AppLanguage.FRENCH).withoutDeviceOnly().language)
    }

    @Test
    fun tags_areTheShippedLanguages_withEuropeanPortuguese() {
        assertNull(AppLanguage.SYSTEM.tag)
        assertEquals(listOf("en", "pt-PT", "es", "fr", "ro"), AppLanguage.entries.drop(1).map { it.tag })
        assertEquals("pt", AppLanguage.PORTUGUESE.language)
        assertEquals("PT", AppLanguage.PORTUGUESE.region)
        assertEquals("", AppLanguage.FRENCH.region)
    }

    @Test
    fun fromTag_readsAnyRegionOrSeparator_andFallsBackToSystem() {
        assertEquals(AppLanguage.PORTUGUESE, AppLanguage.fromTag("pt-BR"))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromTag("en_US"))
        assertEquals(AppLanguage.ROMANIAN, AppLanguage.fromTag(" RO "))
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromTag("de-DE"))
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromTag(""))
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromTag(null))
    }

    @Test
    fun keepInStep_appliesTheStoredChoice_andEveryChange() = runTest {
        val g = TestGraph(settings = AppSettings(language = AppLanguage.FRENCH))
        val job = launch { g.keepLanguageInStep() }
        advanceUntilIdle()
        g.updateSettings { it.copy(language = AppLanguage.ROMANIAN) }
        advanceUntilIdle()
        g.updateSettings { it.copy(language = AppLanguage.SYSTEM) }
        advanceUntilIdle()
        assertEquals(listOf(AppLanguage.FRENCH, AppLanguage.ROMANIAN, AppLanguage.SYSTEM), g.languagePlatform.applied)
        job.cancel()
    }

    @Test
    fun keepInStep_takesAChangeMadeInThePlatformSettings_first() = runTest {
        // Android 13+'s app language (or the iOS Settings app) was changed while the app was closed.
        val g = TestGraph(settings = AppSettings(language = AppLanguage.FRENCH))
        g.languagePlatform.system = AppLanguage.SPANISH
        val job = launch { g.keepLanguageInStep() }
        advanceUntilIdle()
        assertEquals(AppLanguage.SPANISH, g.settingsRepo.settings.first().language)
        assertEquals(listOf(AppLanguage.SPANISH), g.languagePlatform.applied)
        job.cancel()
    }

    @Test
    fun reconcile_withoutAPlatformSetting_keepsTheStoredChoice() = runTest {
        val g = TestGraph(settings = AppSettings(language = AppLanguage.ENGLISH))
        g.keepLanguageInStep.reconcile()
        assertEquals(AppLanguage.ENGLISH, g.settingsRepo.settings.first().language)
        g.languagePlatform.system = AppLanguage.SYSTEM
        g.keepLanguageInStep.reconcile()
        assertEquals(AppLanguage.SYSTEM, g.settingsRepo.settings.first().language)
    }

    @Test
    fun restore_keepsThisDevicesLanguage() = runTest {
        val g = TestGraph(settings = AppSettings(language = AppLanguage.ROMANIAN))
        val json = g.exportBackup()
        g.updateSettings { it.copy(language = AppLanguage.PORTUGUESE) }
        g.restoreBackup(json)
        assertEquals(AppLanguage.PORTUGUESE, g.settingsRepo.settings.first().language)
    }
}
