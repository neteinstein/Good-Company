package com.neteinstein.goodcompany.core.domain.model

/** A newer build of the sideload app that can be downloaded and installed. */
data class AppUpdate(
    /** Android `versionCode` of that build; the installed build is older when this is bigger. */
    val versionCode: Long,
    /** What to show the user, e.g. `1.0.0-test.42`. */
    val versionName: String,
    val apkUrl: String,
    /** Size of the APK, when the source says (used for progress and as a sanity check). */
    val sizeBytes: Long? = null,
)
