package com.primeos.mdm.enrollment

import com.primeos.mdm.admin.AdminAccessGuard
import com.primeos.mdm.device.DeviceType
import com.primeos.mdm.enterprise.AndroidManagementService
import com.primeos.mdm.enterprise.GmsEnterpriseRepository
import com.primeos.mdm.enterprise.NoGmsEnterpriseException
import com.primeos.mdm.organization.OrganizationNotFoundException
import com.primeos.mdm.organization.OrganizationRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class GmsEnrollmentTokenService(
    private val androidManagementService: AndroidManagementService,
    private val organizationRepository: OrganizationRepository,
    private val gmsEnterpriseRepository: GmsEnterpriseRepository,
    private val enrollmentTokenRepository: EnrollmentTokenRepository,
    private val adminAccessGuard: AdminAccessGuard,
) {

    @Transactional
    fun issueGmsEnrollmentToken(organizationId: UUID, request: GmsEnrollmentTokenRequest): EnrollmentToken {
        adminAccessGuard.requireOrganizationAccess(organizationId)
        val organization = organizationRepository.findById(organizationId)
            .orElseThrow { OrganizationNotFoundException(organizationId) }

        val gmsEnterprise = gmsEnterpriseRepository.findByOrganizationId(organizationId)
            ?: throw NoGmsEnterpriseException(organizationId)

        val googleToken = androidManagementService.createEnrollmentToken(
            enterpriseName = gmsEnterprise.enterpriseName,
            policyName = request.policyName,
            oneTimeOnly = request.oneTimeOnly,
            allowPersonalUsage = request.allowPersonalUsage,
            durationSeconds = request.durationSeconds,
        )

        return enrollmentTokenRepository.save(
            EnrollmentToken(
                organization = organization,
                deviceType = DeviceType.GMS,
                tokenValue = googleToken.value,
                qrCodeData = googleToken.qrCode,
                maxUses = if (request.oneTimeOnly) 1 else Int.MAX_VALUE,
                expiresAt = googleToken.expirationTimestamp?.let { Instant.parse(it) },
                // Full raw response, since default_policy_id can't be
                // resolved yet - see GmsEnrollmentTokenRequest.policyName.
                metadata = googleToken.toString(),
            )
        )
    }
}
