package com.primeos.mdm.enrollment

import com.primeos.mdm.admin.AdminAccessGuard
import com.primeos.mdm.device.DeviceType
import com.primeos.mdm.enterprise.AndroidManagementService
import com.primeos.mdm.enterprise.GmsEnterpriseRepository
import com.primeos.mdm.enterprise.NoGmsEnterpriseException
import com.primeos.mdm.organization.OrganizationNotFoundException
import com.primeos.mdm.organization.OrganizationRepository
import com.primeos.mdm.policy.PolicyNotFoundException
import com.primeos.mdm.policy.PolicyRepository
import com.primeos.mdm.policy.PolicyTranslationService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class GmsEnrollmentTokenService(
    private val androidManagementService: AndroidManagementService,
    private val organizationRepository: OrganizationRepository,
    private val gmsEnterpriseRepository: GmsEnterpriseRepository,
    private val policyRepository: PolicyRepository,
    private val policyTranslationService: PolicyTranslationService,
    private val enrollmentTokenRepository: EnrollmentTokenRepository,
    private val adminAccessGuard: AdminAccessGuard,
) {

    // request.policyId used to be a raw, hand-typed Android Management API
    // resource string (e.g. "enterprises/LC00abc123/policies/default") that
    // the caller had to copy from a separate /sync call - any mismatch
    // between that pasted string and this org's actual enterprise ID (a
    // stale value from a different org, a placeholder never replaced, or a
    // policy that was never synced yet) produced the same opaque Google 400
    // ("policy_name is expected to start with enterprises/{id}/policies/"),
    // repeatedly, since nothing on our side validated it first.
    //
    // Now the caller passes our own internal Policy id, same as every other
    // policy-referencing endpoint in this app. This resolves it, verifies it
    // belongs to THIS organization (a SUPER_ADMIN could otherwise pass a
    // policyId from a different org and pass AdminAccessGuard's check
    // regardless), then syncs it via PolicyTranslationService itself - which
    // both pushes the current definition to Google and computes the
    // correctly-scoped gmsPolicyName in one guaranteed-consistent step.
    // There is no longer a hand-copied string that can go stale or belong
    // to the wrong enterprise.
    @Transactional
    fun issueGmsEnrollmentToken(organizationId: UUID, request: GmsEnrollmentTokenRequest): EnrollmentToken {
        adminAccessGuard.requireOrganizationAccess(organizationId)
        val organization = organizationRepository.findById(organizationId)
            .orElseThrow { OrganizationNotFoundException(organizationId) }

        val gmsEnterprise = gmsEnterpriseRepository.findByOrganizationId(organizationId)
            ?: throw NoGmsEnterpriseException(organizationId)

        val policy = policyRepository.findById(request.policyId).orElseThrow { PolicyNotFoundException(request.policyId) }
        if (policy.organization.id != organizationId) {
            throw GmsPolicyOrganizationMismatchException(request.policyId, organizationId)
        }

        val syncResult = policyTranslationService.syncPolicy(request.policyId)
        val gmsPolicyName = syncResult.gmsPolicyName
            ?: error("Policy ${request.policyId} did not sync to a GMS policy name despite organization $organizationId already having a GMS enterprise - this should be unreachable")

        val googleToken = androidManagementService.createEnrollmentToken(
            enterpriseName = gmsEnterprise.enterpriseName,
            policyName = gmsPolicyName,
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
                defaultPolicy = policy,
                maxUses = if (request.oneTimeOnly) 1 else Int.MAX_VALUE,
                expiresAt = googleToken.expirationTimestamp?.let { Instant.parse(it) },
                metadata = googleToken.toString(),
            )
        )
    }
}
