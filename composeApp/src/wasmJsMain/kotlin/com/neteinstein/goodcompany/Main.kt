package com.neteinstein.goodcompany

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.neteinstein.goodcompany.di.initKoin
import kotlinx.browser.document
import kotlinx.browser.window

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    initKoin()
    // e.g. index.html?demo&tab=together&theme=dark — see LaunchOptions.
    val launch = LaunchOptions.parse(window.location.search)
    ComposeViewport(document.body!!) { App(launch) }
}
