package org.goodcompany.app.core.domain.model

import kotlinx.serialization.Serializable
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/** Issue #166: how often the automatic Drive backup runs (Android only). Daily by default. */
@Serializable
enum class AutoBackupFrequency(val every: Duration) {
    DAILY(1.days),
    WEEKLY(7.days),
    OFF(Duration.INFINITE),
}

/** Why an automatic Drive backup didn't run or didn't work, so Settings can say so plainly. */
@Serializable
enum class AutoBackupProblem {
    /** No backup password on this device: nothing is ever uploaded in the clear (D4). */
    NO_PASSWORD,

    /** Google wants the user to sign in or agree again: one "Back up now" in the app fixes it. */
    SIGN_IN_NEEDED,

    /** No OAuth client for this build (package + signing certificate): see docs/GOOGLE_DRIVE.md. */
    NOT_CONFIGURED,
    UNAUTHORIZED,
    FORBIDDEN,
    STORAGE_FULL,
    NETWORK,
    OTHER,
}

/** The last time the automatic backup tried, and what went wrong ([problem] is null when it worked). */
@Serializable
data class AutoBackupRun(val at: Instant, val problem: AutoBackupProblem? = null) {
    val ok: Boolean get() = problem == null
}
