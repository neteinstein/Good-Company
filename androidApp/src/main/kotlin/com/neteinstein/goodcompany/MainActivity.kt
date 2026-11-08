package com.neteinstein.goodcompany

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.neteinstein.goodcompany.core.domain.usecase.ShareInbox
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {
    private val shareInbox: ShareInbox by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        // Draw behind the system bars; Compose Scaffolds consume the insets.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { App() }
        // "Share → Good Company" from Instagram, LinkedIn, the gallery… (not again after a configuration change).
        if (savedInstanceState == null) receiveShare(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        receiveShare(intent)
    }

    private fun receiveShare(intent: Intent?) {
        ShareIntents.read(intent, contentResolver)?.let(shareInbox::offer)
    }
}
