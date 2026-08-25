package com.primeos.mdm.enrollment

data class GmsEnrollmentTokenRequest(
    // Raw Android Management API policy resource name, e.g.
    // "enterprises/LC00abc123/policies/default". See GmsEnrollmentTokenService
    // for why this isn't resolved from our internal `policies` table yet.
    val policyName: String,
    val oneTimeOnly: Boolean = false,
    val allowPersonalUsage: String = "PERSONAL_USAGE_DISALLOWED",
    val durationSeconds: Long? = null,
)
