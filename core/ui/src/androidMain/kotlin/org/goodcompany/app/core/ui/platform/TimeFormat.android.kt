package org.goodcompany.app.core.ui.platform

import android.content.Context
import kotlinx.datetime.LocalTime
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/** Reads the device's 12/24-hour setting; set once by the Application ([initTimeFormat]). */
private var deviceIs24Hour: (() -> Boolean)? = null

/** Lets times follow the device's 12/24-hour setting (not just the locale). Called from the Application's onCreate. */
fun initTimeFormat(context: Context) {
    val app = context.applicationContext
    deviceIs24Hour = { android.text.format.DateFormat.is24HourFormat(app) }
}

actual fun uses24HourClock(): Boolean = deviceIs24Hour?.invoke() ?: localeIs24Hour()

actual fun LocalTime.clockLabel(): String {
    val locale = Locale.getDefault()
    val format = if (deviceIs24Hour != null) {
        // The locale's own pattern for the device's clock ("H:mm", "h:mm a", "HH 'h' mm"…).
        SimpleDateFormat(android.text.format.DateFormat.getBestDateTimePattern(locale, if (uses24HourClock()) "Hm" else "hm"), locale)
    } else {
        java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT, locale)
    }
    val at = Calendar.getInstance().apply {
        clear()
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
    }
    return format.format(at.time)
}

/** Without the device setting (tests, before the Application ran): the locale's short time pattern. */
private fun localeIs24Hour(): Boolean =
    (java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT, Locale.getDefault()) as? SimpleDateFormat)
        ?.toPattern()?.contains('a') != true
