package com.primeos.mdm.enrollment

import java.util.UUID

data class NonGmsEnrollmentTokenRequest(
    // Pre-assigns a policy to devices provisioned from this token; null is
    // fine, an admin can assign one later.
    val defaultPolicyId: UUID? = null,
    // >1 supports bulk-provisioning many devices from one printed/shared
    // QR code - a real need for kiosk-style deployments.
    val maxUses: Int = 1,
)
