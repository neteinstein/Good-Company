package com.neteinstein.goodcompany.di

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
}

/** Parameterless overload for callers that can't pass a Kotlin lambda (Swift). */
fun initKoin() = initKoin {}
