package com.primeos.mdm.enrollment

import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/organizations/{organizationId}/gms-enrollment-tokens")
class GmsEnrollmentTokenController(
    private val gmsEnrollmentTokenService: GmsEnrollmentTokenService,
) {

    @PostMapping
    fun create(
        @PathVariable organizationId: UUID,
        @RequestBody request: GmsEnrollmentTokenRequest,
    ): Map<String, Any?> {
        val token = gmsEnrollmentTokenService.issueGmsEnrollmentToken(organizationId, request)
        return mapOf(
            "id" to token.id,
            // The literal value a device (or a QR code built from it) uses
            // to enroll - treat this as a secret, it's a bearer credential.
            "tokenValue" to token.tokenValue,
            "qrCodeData" to token.qrCodeData,
            "expiresAt" to token.expiresAt,
        )
    }
}
