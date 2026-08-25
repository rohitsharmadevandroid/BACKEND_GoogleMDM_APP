package com.primeos.mdm.dpc

import com.primeos.mdm.command.CommandParams
import com.primeos.mdm.command.CommandType
import com.primeos.mdm.policy.translator.CustomDpcPolicyPayload
import java.util.UUID

data class DpcCheckInRequest(
    val osVersion: String? = null,
    val model: String? = null,
    val manufacturer: String? = null,
    // The DPC echoes back the policy version it last actually applied, so
    // we only send the (potentially large) policy payload again when it's
    // actually changed.
    val lastPolicyVersionApplied: Int? = null,
)

data class DpcCheckInResponse(
    // Null means "no change since lastPolicyVersionApplied" - not "no policy".
    val policy: CustomDpcPolicyPayload?,
    val policyVersion: Int?,
    val pendingCommands: List<DpcPendingCommand>,
    // Lets us adjust polling cadence later without an app update.
    val checkInIntervalSeconds: Long,
)

data class DpcPendingCommand(
    val commandId: UUID,
    val type: CommandType,
    val params: CommandParams,
)
