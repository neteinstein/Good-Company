package com.neteinstein.goodcompany.core.domain.model

/** Why an AI request failed. The UI turns [reason] into a localised message. */
class AiFailure(val reason: Reason, detail: String? = null) : Exception(detail ?: reason.name) {
    enum class Reason { INVALID_KEY, RATE_LIMITED, SERVICE_UNAVAILABLE, REFUSED, TRUNCATED, TOO_SLOW, OTHER }
}
