package com.primeos.mdm.device

import com.primeos.mdm.admin.AdminAccessGuard
import com.primeos.mdm.enterprise.AndroidManagementService
import com.primeos.mdm.enterprise.GmsEnterpriseRepository
import com.primeos.mdm.enterprise.NoGmsEnterpriseException
import com.primeos.mdm.policy.PolicyNotFoundException
import com.primeos.mdm.policy.PolicyRepository
import com.primeos.mdm.policy.PolicyTranslationService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

// Migrates a device that's currently Device Owner via our own custom
// (non-GMS) DPC into being managed for real via the Android Management
// API / Google's Android Device Policy app - Google's documented
// DPC-migration flow (enterprises.migrationTokens.create), a completely
// different thing from a fresh GMS enrollment token.
//
// This only mints the migration token; the actual migration happens
// on-device via DpcMigrationClient.migrate(). The hard constraint (verified
// against the real AMAPI SDK jar, not guessed) is that two of the four
// required fields - deviceId and userId - only exist as the output of a
// successful on-device AccountSetupClient round trip; this service accepts
// them as given from the caller rather than trying to derive them, since
// it has no way to produce them itself.
@Service
class DeviceMigrationService(
    private val deviceRepository: DeviceRepository,
    private val gmsEnterpriseRepository: GmsEnterpriseRepository,
    private val policyRepository: PolicyRepository,
    private val policyTranslationService: PolicyTranslationService,
    private val androidManagementService: AndroidManagementService,
    private val gmsMigrationTokenRepository: GmsMigrationTokenRepository,
    private val adminAccessGuard: AdminAccessGuard,
) {

    @Transactional
    fun createMigrationToken(deviceId: UUID, request: GmsMigrationTokenRequest): GmsMigrationTokenResponse {
        val device = deviceRepository.findById(deviceId).orElseThrow { DeviceNotFoundException(deviceId) }
        adminAccessGuard.requireOrganizationAccess(device.organization.id!!)

        val gmsEnterprise = gmsEnterpriseRepository.findByOrganizationId(device.organization.id!!)
            ?: throw NoGmsEnterpriseException(device.organization.id!!)

        val policy = policyRepository.findById(request.policyId).orElseThrow { PolicyNotFoundException(request.policyId) }
        if (policy.organization.id != device.organization.id) {
            throw PolicyOrganizationMismatchException(request.policyId, deviceId)
        }

        val syncResult = policyTranslationService.syncPolicy(request.policyId)
        val gmsPolicyName = syncResult.gmsPolicyName
            ?: error(
                "Policy ${request.policyId} did not sync to a GMS policy name despite organization " +
                    "${device.organization.id} already having a GMS enterprise - this should be unreachable"
            )

        val migrationToken = androidManagementService.createMigrationToken(
            enterpriseName = gmsEnterprise.enterpriseName,
            playDeviceId = request.playDeviceId,
            playUserId = request.playUserId,
            policyName = gmsPolicyName,
            ttlSeconds = request.ttlSeconds,
            additionalData = request.additionalData,
        )

        // Only persisted once Google has actually accepted the request -
        // same "don't record a thing that didn't happen" discipline as
        // every other Google-backed resource in this app (enrollment
        // tokens, commands).
        gmsMigrationTokenRepository.save(
            GmsMigrationToken(
                organization = device.organization,
                device = device,
                policy = policy,
                playDeviceId = request.playDeviceId,
                playUserId = request.playUserId,
                tokenValue = migrationToken.value ?: "",
                googleName = migrationToken.name,
                expiresAt = migrationToken.expireTime?.let { Instant.parse(it) },
            )
        )

        return GmsMigrationTokenResponse(
            value = migrationToken.value,
            name = migrationToken.name,
            expireTime = migrationToken.expireTime,
        )
    }

    // Purely for admin visibility on the Enrollment Tokens dashboard page -
    // see the note on GmsMigrationToken for why this is a separate list
    // from EnrollmentTokenQueryService's, not merged into it.
    @Transactional(readOnly = true)
    fun listByOrganization(organizationId: UUID): List<GmsMigrationTokenSummary> {
        adminAccessGuard.requireOrganizationAccess(organizationId)
        return gmsMigrationTokenRepository.findByOrganizationIdOrderByCreatedAtDesc(organizationId).map {
            GmsMigrationTokenSummary(
                id = it.id!!,
                deviceId = it.device.id!!,
                deviceName = it.device.displayName ?: it.device.model,
                policyId = it.policy.id!!,
                policyName = it.policy.name,
                tokenValue = it.tokenValue,
                expiresAt = it.expiresAt,
                createdAt = it.createdAt,
            )
        }
    }
}
