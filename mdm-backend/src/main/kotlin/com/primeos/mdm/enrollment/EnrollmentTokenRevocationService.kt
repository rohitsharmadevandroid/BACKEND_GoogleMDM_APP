package com.primeos.mdm.enrollment

import com.primeos.mdm.admin.AdminAccessGuard
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

class EnrollmentTokenNotFoundException(tokenId: UUID) : RuntimeException("No enrollment token with id $tokenId")

@Service
class EnrollmentTokenRevocationService(
    private val enrollmentTokenRepository: EnrollmentTokenRepository,
    private val adminAccessGuard: AdminAccessGuard,
) {

    // Idempotent by design: revoking an already-REVOKED (or CONSUMED, or
    // EXPIRED) token just re-saves the same/target status rather than
    // erroring - there's no meaningful "already revoked" failure mode an
    // admin needs to react to differently.
    @Transactional
    fun revoke(tokenId: UUID): EnrollmentTokenSummary {
        val token = enrollmentTokenRepository.findById(tokenId).orElseThrow { EnrollmentTokenNotFoundException(tokenId) }
        adminAccessGuard.requireOrganizationAccess(token.organization.id!!)

        token.status = EnrollmentTokenStatus.REVOKED
        val saved = enrollmentTokenRepository.save(token)

        return EnrollmentTokenSummary(
            id = saved.id!!,
            deviceType = saved.deviceType,
            tokenValue = saved.tokenValue,
            qrCodeData = saved.qrCodeData,
            status = saved.status,
            maxUses = saved.maxUses,
            usedCount = saved.usedCount,
            expiresAt = saved.expiresAt,
            createdAt = saved.createdAt,
        )
    }
}
