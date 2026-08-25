package com.primeos.mdm.command

import java.time.Instant
import java.util.UUID

data class CommandSummary(
    val id: UUID,
    val commandType: CommandType,
    val status: CommandStatus,
    val payload: CommandParams,
    val errorMessage: String?,
    val dispatchedAt: Instant?,
    val completedAt: Instant?,
    val createdAt: Instant?,
)
