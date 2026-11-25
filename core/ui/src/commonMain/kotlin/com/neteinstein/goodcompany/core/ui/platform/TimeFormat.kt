package com.neteinstein.goodcompany.core.ui.platform

import kotlinx.datetime.LocalTime

/**
 * A time of day the way the device shows times (issue #167): its 12- or 24-hour setting and the locale's marks
 * ("19:30", "7:30 PM"). Never a hard-coded pattern.
 */
expect fun LocalTime.clockLabel(): String

/** Whether the device shows times on a 24-hour clock; time pickers follow it. */
expect fun uses24HourClock(): Boolean
