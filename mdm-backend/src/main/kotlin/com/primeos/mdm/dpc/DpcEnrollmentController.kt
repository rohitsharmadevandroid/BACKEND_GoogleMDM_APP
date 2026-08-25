package com.primeos.mdm.dpc

import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

// The only unauthenticated /api/dpc/** endpoint (see DeviceAuthenticationFilter) -
// the enrollment token itself is the one-time proof of identity here.
@RestController
@RequestMapping("/api/dpc")
class DpcEnrollmentController(
    private val dpcEnrollmentService: DpcEnrollmentService,
) {

    @PostMapping("/enroll")
    fun enroll(@RequestBody request: DpcEnrollRequest): DpcEnrollResponse = dpcEnrollmentService.enroll(request)
}
