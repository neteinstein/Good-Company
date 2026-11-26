package com.neteinstein.goodcompany.core.domain.usecase

import com.neteinstein.goodcompany.core.domain.model.AddressKind
import com.neteinstein.goodcompany.core.domain.model.PostalAddress

/** Home and work postal addresses (issue #169): free text, no validation or geocoding, never written back or sent to the AI. */
object PostalAddresses {
    /**
     * Applies what the About editor holds for each kind in [typed] (kinds not in it are kept as they are). Text that
     * didn't change keeps its address (and whether it came from the contact); changed text becomes a typed address,
     * which a contacts sync keeps; blank text removes it (a contact one comes back on the next sync). Home first.
     */
    fun edit(current: List<PostalAddress>, typed: Map<AddressKind, String>): List<PostalAddress> =
        AddressKind.entries.mapNotNull { kind ->
            val now = current.firstOrNull { it.kind == kind }
            if (kind !in typed) return@mapNotNull now
            val text = typed.getValue(kind).trim()
            when {
                text.isEmpty() -> null
                now != null && now.text.trim() == text -> now
                else -> PostalAddress(kind, text)
            }
        }
}
