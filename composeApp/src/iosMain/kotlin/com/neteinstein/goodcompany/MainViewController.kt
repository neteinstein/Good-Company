package com.neteinstein.goodcompany

import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

/** Entry point used by iosApp/ContentView.swift. Koin must already be started (iOSApp.init). */
@Suppress("FunctionName", "unused")
fun MainViewController(): UIViewController = ComposeUIViewController { App() }
