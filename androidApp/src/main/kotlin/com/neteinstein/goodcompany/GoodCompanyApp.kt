package com.neteinstein.goodcompany

import android.app.Application
import com.google.firebase.FirebaseApp
import com.neteinstein.goodcompany.core.data.platform.WeeklyIdeasWorker
import com.neteinstein.goodcompany.di.initKoin
import org.koin.android.ext.koin.androidContext

class GoodCompanyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // No-op (returns null) when the build has no google-services.json.
        FirebaseApp.initializeApp(this)
        initKoin { androidContext(this@GoodCompanyApp) }
        WeeklyIdeasWorker.schedule(this)
    }
}
