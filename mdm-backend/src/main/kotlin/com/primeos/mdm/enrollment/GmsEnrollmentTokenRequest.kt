package com.primeos.mdm.enrollment

import java.util.UUID

data class GmsEnrollmentTokenRequest(
    // Our own internal Policy id - same concept as
    // NonGmsEnrollmentTokenRequest.defaultPolicyId. GmsEnrollmentTokenService
    // resolves this, verifies it belongs to this organization, and syncs it
    // to Google itself - the raw Android Management API policy resource
    // name (e.g. "enterprises/LC00abc123/policies/default") this used to
    // require by hand is now computed internally, never typed by a caller.
    val policyId: UUID,
    val oneTimeOnly: Boolean = false,
    val allowPersonalUsage: String = "PERSONAL_USAGE_DISALLOWED",
    val durationSeconds: Long? = null,
)
