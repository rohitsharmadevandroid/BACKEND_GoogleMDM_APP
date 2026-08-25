package com.primeos.mdm.enrollment

import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/enrollment-tokens")
class EnrollmentTokenRevocationController(
    private val enrollmentTokenRevocationService: EnrollmentTokenRevocationService,
) {

    @PostMapping("/{tokenId}/revoke")
    fun revoke(@PathVariable tokenId: UUID): EnrollmentTokenSummary = enrollmentTokenRevocationService.revoke(tokenId)
}
