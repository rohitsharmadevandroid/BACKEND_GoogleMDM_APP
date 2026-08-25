package com.primeos.mdm.dpc

import java.util.UUID

data class DpcEnrollRequest(val enrollmentToken: String)

data class DpcEnrollResponse(
    val deviceId: UUID,
    // Shown exactly once - the DPC app must store this itself; we never
    // persist it, only its hash (see DeviceCredentialService).
    val deviceApiKey: String,
    val checkInIntervalSeconds: Long,
)
