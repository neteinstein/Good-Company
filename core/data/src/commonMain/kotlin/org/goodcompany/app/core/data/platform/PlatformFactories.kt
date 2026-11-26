package org.goodcompany.app.core.data.platform

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import org.goodcompany.app.core.data.db.DatabaseDriverFactory
import org.goodcompany.app.core.data.source.CommunicationLogSource
import org.goodcompany.app.core.data.source.ContactsSource
import org.goodcompany.app.core.domain.repository.CalendarReader
import org.goodcompany.app.core.domain.repository.ContactsWriter
import org.goodcompany.app.core.domain.repository.DriveTokenSource
import org.goodcompany.app.core.domain.repository.LocationProvider
import org.goodcompany.app.core.domain.repository.NotificationScheduler
import org.koin.core.scope.Scope

/*
 * The only things a platform genuinely can't share: OS address book, call log, location and
 * local notifications (and, on the Android sideload build, writing back to contacts). These factories are *called* from the single shared Koin module
 * (composeApp/di/AppModule.kt) — they don't register anything themselves.
 * They're Scope extensions so the Android actuals can reach `androidContext()`.
 */

expect fun Scope.createContactsSource(): ContactsSource

expect fun Scope.createCommunicationLogSource(): CommunicationLogSource

/** Mirrors app data onto contacts. Real only in the Android sideload build (WRITE_CONTACTS); [ContactsWriter.None] elsewhere. */
expect fun Scope.createContactsWriter(): ContactsWriter

expect fun Scope.createLocationProvider(): LocationProvider

expect fun Scope.createNotificationScheduler(): NotificationScheduler

/** Reads the device calendars for calendar suggestions (D3): Android CalendarContract, iOS EventKit, none on the web. */
expect fun Scope.createCalendarReader(): CalendarReader

/**
 * SQLite driver per platform: Android `AndroidSqliteDriver`, iOS `NativeSqliteDriver`, web `WebWorkerDriver` over our
 * OPFS worker (`src/wasmJsMain/sqlite-worker`). Every driver turns foreign keys on.
 */
expect fun Scope.createDatabaseDriverFactory(): DatabaseDriverFactory

/**
 * Hooks notification quick actions up to the app (iOS: installs the UNUserNotificationCenter delegate; Android:
 * no-op, a manifest receiver handles them; web: no notifications). Call right after Koin starts.
 */
expect fun installNotificationActionHandling()

/**
 * Drive tokens with no screen, for the automatic backup (issue #166): Android asks Play services for the access the
 * user already granted; iOS and the web have no automatic backup, so theirs always needs the user.
 */
expect fun Scope.createDriveTokenSource(): DriveTokenSource

/** Ktor picks the platform engine (OkHttp / Darwin / JS fetch) from the classpath. */
fun createHttpClient(): HttpClient = HttpClient {
    install(HttpTimeout) {
        // Web-search-backed generations can take a while.
        requestTimeoutMillis = 180_000
        socketTimeoutMillis = 180_000
        connectTimeoutMillis = 20_000
    }
    expectSuccess = false
}
