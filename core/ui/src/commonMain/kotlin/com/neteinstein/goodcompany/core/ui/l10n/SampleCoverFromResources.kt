package com.neteinstein.goodcompany.core.ui.l10n

import com.neteinstein.goodcompany.core.domain.model.ContactPhoto
import com.neteinstein.goodcompany.core.domain.repository.SampleCoverSource
import com.neteinstein.goodcompany.core.ui.resources.Res

/**
 * The demo circle's cover (issue #88): `files/sample_circle_cover.jpg`, an original abstract picture drawn for the app
 * (a sunrise over waves, no third-party photo), within the cover limits.
 */
object SampleCoverFromResources : SampleCoverSource {
    override suspend fun cover(): ContactPhoto? = runCatching { ContactPhoto.cover(Res.readBytes("files/sample_circle_cover.jpg")) }.getOrNull()
}
