package com.primeos.mdm.device

import java.time.Instant
import java.util.UUID

// The unified admin-facing view across both device types - the concrete
// deliverable of "manage both device types through one interface" from
// the original architecture goal. Common fields are top-level; the two
// type-specific identifiers are both present but only one is ever
// non-null for a given device.
data class DeviceSummary(
    val id: UUID,
    val deviceType: DeviceType,
    val displayName: String?,
    val status: DeviceStatus,
    val model: String?,
    val manufacturer: String?,
    val osVersion: String?,
    val lastSeenAt: Instant?,
    val policyId: UUID?,
    val policyName: String?,
    val gmsDeviceResourceName: String?,
    val deviceUid: String?,
)
