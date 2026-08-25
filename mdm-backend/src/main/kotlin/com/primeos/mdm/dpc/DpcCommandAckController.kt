package com.primeos.mdm.dpc

import com.primeos.mdm.device.Device
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestAttribute
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/dpc/commands")
class DpcCommandAckController(
    private val dpcCommandAckService: DpcCommandAckService,
) {

    @PostMapping("/{commandId}/ack")
    fun ack(
        @RequestAttribute("authenticatedDevice") device: Device,
        @PathVariable commandId: UUID,
        @RequestBody request: DpcCommandAckRequest,
    ): Map<String, Any?> {
        val command = dpcCommandAckService.ack(device, commandId, request)
        return mapOf("id" to command.id, "status" to command.status)
    }
}
