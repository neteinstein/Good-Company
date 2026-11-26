package org.goodcompany.app.core.ui.platform

import kotlinx.datetime.LocalTime

actual fun LocalTime.clockLabel(): String = browserTime(hour, minute)

actual fun uses24HourClock(): Boolean = browserIs24Hour()

// The browser's locale and its hour cycle ("19:30", "7:30 PM").
private fun browserTime(hour: Int, minute: Int): String = js(
    """new Date(2000, 0, 1, hour, minute).toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' })""",
)

private fun browserIs24Hour(): Boolean = js(
    """(function () {
        try {
            var c = new Intl.DateTimeFormat([], { hour: 'numeric' }).resolvedOptions().hourCycle;
            return c === 'h23' || c === 'h24';
        } catch (e) { return true; }
    })()""",
)
