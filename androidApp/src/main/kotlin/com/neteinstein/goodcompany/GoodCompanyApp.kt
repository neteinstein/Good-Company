package com.neteinstein.goodcompany

import android.app.Application
import com.google.firebase.FirebaseApp
import com.neteinstein.goodcompany.core.data.platform.DriveAutoBackupWorker
import com.neteinstein.goodcompany.core.data.platform.ReplanNotificationsWorker
import com.neteinstein.goodcompany.core.data.platform.WeeklyIdeasWorker
import com.neteinstein.goodcompany.core.ui.platform.initAppLanguage
import com.neteinstein.goodcompany.core.ui.platform.initTimeFormat
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
        // Issue #167: plan times follow the phone's 12/24-hour setting.
        initTimeFormat(this)
        // Issue #174: the in-app language and Android 13+'s per-app language stay in step.
        initAppLanguage(this)
        WeeklyIdeasWorker.schedule(this)
        ReplanNotificationsWorker.schedule(this)
        // Issue #166: the daily job decides itself whether a backup is due (Daily / Weekly / Off).
        DriveAutoBackupWorker.schedule(this)
        NextUpWidget.keepUpdated(this, get())
        AppShortcuts.keepUpdated(this, get())
    }
}
