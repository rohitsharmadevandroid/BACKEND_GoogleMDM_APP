package com.primeos.mdm.enrollment

import com.primeos.mdm.admin.AdminAccessGuard
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

// Read-only view spanning both GmsEnrollmentTokenService and
// NonGmsEnrollmentTokenService's issued tokens - deliberately separate
// from those two, which are write-focused and issue one type each.
@Service
class EnrollmentTokenQueryService(
    private val enrollmentTokenRepository: EnrollmentTokenRepository,
    private val adminAccessGuard: AdminAccessGuard,
) {

    @Transactional(readOnly = true)
    fun listByOrganization(organizationId: UUID): List<EnrollmentTokenSummary> {
        adminAccessGuard.requireOrganizationAccess(organizationId)
        return enrollmentTokenRepository.findByOrganizationId(organizationId).map {
            EnrollmentTokenSummary(
                id = it.id!!,
                deviceType = it.deviceType,
                tokenValue = it.tokenValue,
                status = it.status,
                maxUses = it.maxUses,
                usedCount = it.usedCount,
                expiresAt = it.expiresAt,
                createdAt = it.createdAt,
            )
        }
    }
}
