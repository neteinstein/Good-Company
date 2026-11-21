package com.neteinstein.goodcompany

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.neteinstein.goodcompany.core.domain.usecase.AppLink
import com.neteinstein.goodcompany.core.domain.usecase.PersonInbox
import com.neteinstein.goodcompany.core.domain.usecase.QuickEntryInbox
import com.neteinstein.goodcompany.core.domain.usecase.ShareInbox
import com.neteinstein.goodcompany.core.domain.usecase.toQuickEntry
import com.neteinstein.goodcompany.shortcuts.AppShortcuts
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {
    private val shareInbox: ShareInbox by inject()
    private val quickEntryInbox: QuickEntryInbox by inject()
    private val personInbox: PersonInbox by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        // Draw behind the system bars; Compose Scaffolds consume the insets.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { App() }
        // "Share → Good Company" from Instagram, LinkedIn, the gallery… or an app-icon shortcut
        // (not again after a configuration change).
        if (savedInstanceState == null) receive(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        receive(intent)
    }

    private fun receive(intent: Intent?) {
        ShareIntents.read(intent, this)?.let(shareInbox::offer)
        receiveShortcut(intent)
    }

    /**
     * `goodcompany://log[/<friendId>]` from an app-icon shortcut: opens the Log sheet (behind the app lock).
     * `goodcompany://person/<friendId>` from a birthday heads-up (issue #133): opens their page.
     */
    private fun receiveShortcut(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        when (val link = intent.dataString?.let(AppLink::parse)) {
            is AppLink.Log -> {
                quickEntryInbox.offer(link.toQuickEntry())
                AppShortcuts.reportUsed(this, link)
            }
            is AppLink.Person -> personInbox.offer(link.friendId)
            else -> Unit
        }
    }
}
