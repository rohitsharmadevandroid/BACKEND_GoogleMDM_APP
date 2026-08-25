package com.primeos.mdm.dpc

import com.primeos.mdm.device.Device
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestAttribute
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/dpc")
class DpcCheckInController(
    private val dpcCheckInService: DpcCheckInService,
) {

    // "authenticatedDevice" is set by DeviceAuthenticationFilter before any
    // request reaches here - see that class for why this isn't Spring
    // Security's own authentication mechanism.
    @PostMapping("/checkin")
    fun checkIn(
        @RequestAttribute("authenticatedDevice") device: Device,
        @RequestBody request: DpcCheckInRequest,
    ): DpcCheckInResponse = dpcCheckInService.checkIn(device, request)
}
