package com.neteinstein.goodcompany.core.domain.util

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

/** Time source + the user's zone. Injected everywhere "now" matters so tests can pin it. */
interface AppClock {
    fun now(): Instant

    val timeZone: TimeZone

    fun today(): LocalDate = now().toLocalDateTime(timeZone).date

    object System : AppClock {
        override fun now(): Instant = Clock.System.now()

        override val timeZone: TimeZone get() = TimeZone.currentSystemDefault()
    }
}

fun Instant.toLocalDate(zone: TimeZone): LocalDate = toLocalDateTime(zone).date
