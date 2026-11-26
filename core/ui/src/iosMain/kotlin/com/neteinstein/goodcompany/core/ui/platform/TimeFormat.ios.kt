package com.neteinstein.goodcompany.core.ui.platform

import kotlinx.datetime.LocalTime
import platform.Foundation.NSCalendar
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSDateFormatterNoStyle
import platform.Foundation.NSDateFormatterShortStyle
import platform.Foundation.NSLocale
import platform.Foundation.currentLocale

actual fun LocalTime.clockLabel(): String {
    val formatter = NSDateFormatter().apply {
        // The short time style follows the locale and the user's 24-Hour Time setting.
        dateStyle = NSDateFormatterNoStyle
        timeStyle = NSDateFormatterShortStyle
        locale = NSLocale.currentLocale
    }
    val at = NSCalendar.currentCalendar.dateBySettingHour(hour.toLong(), minute.toLong(), 0, NSDate(), 0u) ?: return fallback()
    return formatter.stringFromDate(at)
}

actual fun uses24HourClock(): Boolean =
    NSDateFormatter.dateFormatFromTemplate("j", 0u, NSLocale.currentLocale)?.contains('a') != true

private fun LocalTime.fallback() = "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"
