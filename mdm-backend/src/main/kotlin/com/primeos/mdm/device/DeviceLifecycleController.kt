package com.primeos.mdm.device

import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
class DeviceLifecycleController(
    private val deviceLifecycleService: DeviceLifecycleService,
) {

    @DeleteMapping("/api/devices/{deviceId}")
    fun unenroll(
        @PathVariable deviceId: UUID,
        @RequestBody(required = false) request: UnenrollDeviceRequest?,
    ): DeviceSummary = deviceLifecycleService.unenroll(deviceId, request ?: UnenrollDeviceRequest())
}
