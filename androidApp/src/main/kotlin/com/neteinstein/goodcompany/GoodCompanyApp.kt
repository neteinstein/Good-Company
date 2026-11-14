package com.neteinstein.goodcompany

import android.app.Application
import com.google.firebase.FirebaseApp
import com.neteinstein.goodcompany.core.data.platform.WeeklyIdeasWorker
import com.neteinstein.goodcompany.di.initKoin
import com.neteinstein.goodcompany.shortcuts.AppShortcuts
import com.neteinstein.goodcompany.widget.NextUpWidget
import org.koin.android.ext.koin.androidContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

class GoodCompanyApp : Application(), KoinComponent {
    override fun onCreate() {
        super.onCreate()
        // No-op (returns null) when the build has no google-services.json.
        FirebaseApp.initializeApp(this)
        initKoin { androidContext(this@GoodCompanyApp) }
        WeeklyIdeasWorker.schedule(this)
        NextUpWidget.keepUpdated(this, get())
        AppShortcuts.keepUpdated(this, get())
    }
}
