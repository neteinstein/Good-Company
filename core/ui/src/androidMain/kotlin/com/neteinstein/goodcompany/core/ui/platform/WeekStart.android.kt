package com.neteinstein.goodcompany.core.ui.platform

import kotlinx.datetime.DayOfWeek
import java.util.Calendar

actual fun firstDayOfWeek(): DayOfWeek = dayOfWeekFromSundayFirst(Calendar.getInstance().firstDayOfWeek)
