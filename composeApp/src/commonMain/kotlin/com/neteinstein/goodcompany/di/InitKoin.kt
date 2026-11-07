package com.neteinstein.goodcompany.di

import com.neteinstein.goodcompany.core.data.platform.installNotificationActionHandling
import org.koin.core.context.startKoin
import org.koin.dsl.KoinAppDeclaration

/**
 * DI bootstrap — kept apart from the module definitions in [appModule].
 *
 * - Android: `initKoin { androidContext(this@GoodCompanyApp) }` from the Application class.
 * - iOS: `InitKoinKt.doInitKoin()` from `iOSApp.init()` (Kotlin/Native's Obj-C exporter renames
 *   top-level functions that start with `init` by prefixing `do`).
 * - Web: `initKoin()` from `main()`.
 */
fun initKoin(appDeclaration: KoinAppDeclaration) {
    startKoin {
        appDeclaration()
        modules(appModule)
    }
    // Quick actions on catch-up notifications ("Called", "Snooze") need Koin, so wire them after it starts.
    installNotificationActionHandling()
}

/** Parameterless overload for callers that can't pass a Kotlin lambda (Swift). */
fun initKoin() = initKoin {}
