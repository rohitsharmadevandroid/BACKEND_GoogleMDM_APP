package com.primeos.mdm.enterprise

import com.primeos.mdm.admin.AdminAccessGuard
import com.primeos.mdm.organization.OrganizationNotFoundException
import com.primeos.mdm.organization.OrganizationRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class GmsEnterpriseService(
    private val androidManagementService: AndroidManagementService,
    private val organizationRepository: OrganizationRepository,
    private val gmsEnterpriseRepository: GmsEnterpriseRepository,
    private val gmsEnterpriseSignupRepository: GmsEnterpriseSignupRepository,
    @Value("\${mdm.gcp.project-id}")
    private val projectId: String,
    @Value("\${mdm.gcp.android-management-callback-base-url}")
    private val callbackBaseUrl: String,
    // Recorded on the resulting GmsEnterprise row so it's clear which
    // credential backs this project ID. Becomes a real Secret Manager
    // reference once that's wired up - see gms_enterprises.service_account_secret_ref.
    @Value("\${mdm.gcp.android-management-credentials-path}")
    private val credentialsPathDescriptor: String,
    private val adminAccessGuard: AdminAccessGuard,
) {

    // Step 1: hand the admin a Google-hosted URL to complete enterprise
    // sign-up in their browser. We persist which signupUrlName we requested
    // because the callback (step 2) won't tell us - see
    // V10__create_gms_enterprise_signups.sql for why.
    @Transactional
    fun startSignup(organizationId: UUID): String {
        adminAccessGuard.requireOrganizationAccess(organizationId)
        val organization = organizationRepository.findById(organizationId)
            .orElseThrow { OrganizationNotFoundException(organizationId) }

        if (gmsEnterpriseRepository.findByOrganizationId(organizationId) != null) {
            throw GmsEnterpriseAlreadyExistsException(organizationId)
        }

        val callbackUrl = "$callbackBaseUrl/api/organizations/$organizationId/gms-enterprise/callback"
        val signupUrl = androidManagementService.createSignupUrl(callbackUrl)

        gmsEnterpriseSignupRepository.save(
            GmsEnterpriseSignup(organization = organization, signupUrlName = signupUrl.name)
        )

        return signupUrl.url
    }

    // Step 2: the admin's browser lands here (via Google's redirect) with
    // enterpriseToken. We pair it with the signupUrlName from step 1 to
    // actually create the enterprise, then persist the result.
    //
    // Deliberately NO AdminAccessGuard call here: this is reached via
    // Google's redirect (see SecurityConfig's public callback carve-out),
    // which carries no admin JWT at all - enterpriseToken's one-time
    // possession is this endpoint's actual proof, not an admin session.
    @Transactional
    fun completeSignup(organizationId: UUID, enterpriseToken: String): GmsEnterprise {
        val organization = organizationRepository.findById(organizationId)
            .orElseThrow { OrganizationNotFoundException(organizationId) }

        val pendingSignup = gmsEnterpriseSignupRepository
            .findFirstByOrganizationIdAndConsumedAtIsNullOrderByCreatedAtDesc(organizationId)
            ?: throw NoPendingGmsSignupException(organizationId)

        val enterprise = androidManagementService.createEnterprise(
            signupUrlName = pendingSignup.signupUrlName,
            enterpriseToken = enterpriseToken,
            displayName = organization.name,
        )

        pendingSignup.consumedAt = Instant.now()
        gmsEnterpriseSignupRepository.save(pendingSignup)

        return gmsEnterpriseRepository.save(
            GmsEnterprise(
                organization = organization,
                enterpriseName = enterprise.name,
                gcpProjectId = projectId,
                serviceAccountSecretRef = credentialsPathDescriptor,
            )
        )
    }
}
