package com.primeos.mdm.enrollment

import com.primeos.mdm.device.DeviceType
import java.time.Instant
import java.util.UUID

data class EnrollmentTokenSummary(
    val id: UUID,
    val deviceType: DeviceType,
    val tokenValue: String,
    val status: EnrollmentTokenStatus,
    val maxUses: Int,
    val usedCount: Int,
    val expiresAt: Instant?,
    val createdAt: Instant?,
)
