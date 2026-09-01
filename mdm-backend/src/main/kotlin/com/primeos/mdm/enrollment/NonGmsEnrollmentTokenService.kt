package com.primeos.mdm.enrollment

import com.primeos.mdm.admin.AdminAccessGuard
import com.primeos.mdm.device.DeviceType
import com.primeos.mdm.organization.OrganizationNotFoundException
import com.primeos.mdm.organization.OrganizationRepository
import com.primeos.mdm.policy.PolicyNotFoundException
import com.primeos.mdm.policy.PolicyRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID

@Service
class NonGmsEnrollmentTokenService(
    private val organizationRepository: OrganizationRepository,
    private val policyRepository: PolicyRepository,
    private val enrollmentTokenRepository: EnrollmentTokenRepository,
    private val provisioningQrCodeBuilder: NonGmsProvisioningQrCodeBuilder,
    private val adminAccessGuard: AdminAccessGuard,
) {

    private val secureRandom = SecureRandom()

    @Transactional
    fun issueToken(organizationId: UUID, request: NonGmsEnrollmentTokenRequest): EnrollmentToken {
        adminAccessGuard.requireOrganizationAccess(organizationId)
        val organization = organizationRepository.findById(organizationId)
            .orElseThrow { OrganizationNotFoundException(organizationId) }

        val defaultPolicy = request.defaultPolicyId?.let { policyId ->
            policyRepository.findById(policyId).orElseThrow { PolicyNotFoundException(policyId) }
        }

        val tokenValue = generateTokenValue()

        return enrollmentTokenRepository.save(
            EnrollmentToken(
                organization = organization,
                deviceType = DeviceType.NON_GMS,
                tokenValue = tokenValue,
                // Real Device Owner provisioning QR once
                // mdm.dpc.device-admin-component-name/apk-download-url/
                // apk-signature-checksum are all configured; falls back to
                // the placeholder {"enrollmentToken":"..."} until then (see
                // NonGmsProvisioningQrCodeBuilder for why it's not built here
                // unconditionally).
                qrCodeData = provisioningQrCodeBuilder.build(tokenValue) ?: """{"enrollmentToken":"$tokenValue"}""",
                defaultPolicy = defaultPolicy,
                maxUses = request.maxUses,
            )
        )
    }

    private fun generateTokenValue(): String {
        val bytes = ByteArray(24)
        secureRandom.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
}
