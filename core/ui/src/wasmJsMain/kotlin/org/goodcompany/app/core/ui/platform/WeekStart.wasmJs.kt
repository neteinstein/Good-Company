package org.goodcompany.app.core.ui.platform

import kotlinx.datetime.DayOfWeek

actual fun firstDayOfWeek(): DayOfWeek = dayOfWeekFromIso(browserFirstDay())

// Intl.Locale week info, where the browser has it (getWeekInfo(), or the older weekInfo); 0 when unknown.
private fun browserFirstDay(): Int = js(
    """(function () {
        try {
            var l = new Intl.Locale(navigator.language);
            var w = l.getWeekInfo ? l.getWeekInfo() : l.weekInfo;
            return w && w.firstDay ? w.firstDay : 0;
        } catch (e) { return 0; }
    })()""",
)
