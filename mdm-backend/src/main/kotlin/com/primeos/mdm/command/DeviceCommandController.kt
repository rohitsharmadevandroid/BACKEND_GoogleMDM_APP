package com.primeos.mdm.command

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/devices/{deviceId}/commands")
class DeviceCommandController(
    private val deviceCommandService: DeviceCommandService,
) {

    @PostMapping
    fun issueCommand(
        @PathVariable deviceId: UUID,
        @RequestParam commandType: CommandType,
        @RequestBody(required = false) params: CommandParams?,
    ): Map<String, Any?> {
        val command = deviceCommandService.issueCommand(deviceId, commandType, params ?: CommandParams())
        return mapOf(
            "id" to command.id,
            "status" to command.status,
            "gmsCommandResourceName" to command.gmsCommandResourceName,
            "dispatchedAt" to command.dispatchedAt,
        )
    }

    @GetMapping
    fun list(@PathVariable deviceId: UUID): List<CommandSummary> = deviceCommandService.listCommands(deviceId)
}
