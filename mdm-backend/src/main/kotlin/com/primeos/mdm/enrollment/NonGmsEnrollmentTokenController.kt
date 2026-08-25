package com.primeos.mdm.enrollment

import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/organizations/{organizationId}/non-gms-enrollment-tokens")
class NonGmsEnrollmentTokenController(
    private val nonGmsEnrollmentTokenService: NonGmsEnrollmentTokenService,
) {

    @PostMapping
    fun issue(
        @PathVariable organizationId: UUID,
        @RequestBody(required = false) request: NonGmsEnrollmentTokenRequest?,
    ): Map<String, Any?> {
        val token = nonGmsEnrollmentTokenService.issueToken(organizationId, request ?: NonGmsEnrollmentTokenRequest())
        return mapOf(
            "id" to token.id,
            "tokenValue" to token.tokenValue,
            "qrCodeData" to token.qrCodeData,
            "maxUses" to token.maxUses,
        )
    }
}
