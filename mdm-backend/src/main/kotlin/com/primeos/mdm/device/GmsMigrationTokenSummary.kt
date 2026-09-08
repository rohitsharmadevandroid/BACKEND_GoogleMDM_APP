package com.primeos.mdm.device

import java.time.Instant
import java.util.UUID

data class GmsMigrationTokenSummary(
    val id: UUID,
    val deviceId: UUID,
    val deviceName: String?,
    val policyId: UUID,
    val policyName: String,
    val tokenValue: String,
    val expiresAt: Instant?,
    val createdAt: Instant?,
)
