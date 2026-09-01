package com.primeos.mdm.dpc

import com.fasterxml.jackson.databind.ObjectMapper
import com.primeos.mdm.command.Command
import com.primeos.mdm.command.CommandNotFoundException
import com.primeos.mdm.command.CommandRepository
import com.primeos.mdm.command.CommandStatus
import com.primeos.mdm.device.Device
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class DpcCommandAckService(
    private val commandRepository: CommandRepository,
    private val objectMapper: ObjectMapper,
) {

    @Transactional
    fun ack(device: Device, commandId: UUID, request: DpcCommandAckRequest): Command {
        val command = commandRepository.findById(commandId).orElseThrow { CommandNotFoundException(commandId) }
        // Don't reveal that a command exists at all if it belongs to a
        // different device - same 404 either way.
        if (command.device.id != device.id) {
            throw CommandNotFoundException(commandId)
        }

        command.status = request.status
        command.errorMessage = request.errorMessage
        if (request.resultData != null) {
            command.resultData = objectMapper.writeValueAsString(request.resultData)
        }
        if (request.status == CommandStatus.COMPLETED || request.status == CommandStatus.FAILED) {
            command.completedAt = Instant.now()
        }

        return commandRepository.save(command)
    }
}
