package com.primeos.mdm.dpc

import com.primeos.mdm.command.CommandStatus

data class DpcCommandAckRequest(
    val status: CommandStatus,
    val errorMessage: String? = null,
)
