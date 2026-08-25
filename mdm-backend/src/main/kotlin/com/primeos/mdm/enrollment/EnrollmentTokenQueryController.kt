package com.primeos.mdm.enrollment

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/organizations/{organizationId}/enrollment-tokens")
class EnrollmentTokenQueryController(
    private val enrollmentTokenQueryService: EnrollmentTokenQueryService,
) {

    @GetMapping
    fun list(@PathVariable organizationId: UUID): List<EnrollmentTokenSummary> =
        enrollmentTokenQueryService.listByOrganization(organizationId)
}
