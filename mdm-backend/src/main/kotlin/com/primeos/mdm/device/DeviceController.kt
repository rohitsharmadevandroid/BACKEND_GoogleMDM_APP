package com.primeos.mdm.device

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
class DeviceController(
    private val deviceService: DeviceService,
) {

    @GetMapping("/api/organizations/{organizationId}/devices")
    fun listByOrganization(@PathVariable organizationId: UUID): List<DeviceSummary> =
        deviceService.listByOrganization(organizationId)

    @GetMapping("/api/devices/{deviceId}")
    fun get(@PathVariable deviceId: UUID): DeviceSummary = deviceService.getSummary(deviceId)

    // policyId present -> assign/change policy; policyId null -> remove
    // the device's policy assignment entirely.
    @PutMapping("/api/devices/{deviceId}/policy")
    fun assignPolicy(
        @PathVariable deviceId: UUID,
        @RequestBody request: AssignDevicePolicyRequest,
    ): DeviceSummary = deviceService.assignPolicy(deviceId, request.policyId)

    @PutMapping("/api/devices/{deviceId}/display-name")
    fun updateDisplayName(
        @PathVariable deviceId: UUID,
        @RequestBody request: UpdateDeviceDisplayNameRequest,
    ): DeviceSummary = deviceService.updateDisplayName(deviceId, request.displayName)
}
