package com.neteinstein.goodcompany.core.data.platform

import com.neteinstein.goodcompany.core.data.db.DatabaseDriverFactory
import com.neteinstein.goodcompany.core.data.source.CommunicationLogSource
import com.neteinstein.goodcompany.core.data.source.ContactsSource
import com.neteinstein.goodcompany.core.domain.repository.ContactsWriter
import com.neteinstein.goodcompany.core.domain.repository.LocationProvider
import com.neteinstein.goodcompany.core.domain.repository.NotificationScheduler
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
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
