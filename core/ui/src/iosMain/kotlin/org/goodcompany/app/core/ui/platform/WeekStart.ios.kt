package org.goodcompany.app.core.ui.platform

import kotlinx.datetime.DayOfWeek
import platform.Foundation.NSCalendar

actual fun firstDayOfWeek(): DayOfWeek = dayOfWeekFromSundayFirst(NSCalendar.currentCalendar.firstWeekday.toInt())
