package org.goodcompany.app

import android.app.Application
import com.google.firebase.FirebaseApp
import org.goodcompany.app.core.data.platform.DriveAutoBackupWorker
import org.goodcompany.app.core.data.platform.ReplanNotificationsWorker
import org.goodcompany.app.core.data.platform.WeeklyIdeasWorker
import org.goodcompany.app.core.ui.platform.initAppLanguage
import org.goodcompany.app.core.ui.platform.initTimeFormat
import org.goodcompany.app.di.initKoin
import org.goodcompany.app.shortcuts.AppShortcuts
import org.goodcompany.app.widget.NextUpWidget
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
