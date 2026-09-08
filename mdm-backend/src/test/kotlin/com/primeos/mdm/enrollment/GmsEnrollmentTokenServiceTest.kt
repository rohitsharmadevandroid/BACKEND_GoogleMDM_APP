package com.primeos.mdm.enrollment

import com.google.api.services.androidmanagement.v1.model.EnrollmentToken as GoogleEnrollmentToken
import com.primeos.mdm.admin.AdminAccessGuard
import com.primeos.mdm.device.DeviceType
import com.primeos.mdm.enterprise.AndroidManagementService
import com.primeos.mdm.enterprise.GmsEnterprise
import com.primeos.mdm.enterprise.GmsEnterpriseRepository
import com.primeos.mdm.enterprise.NoGmsEnterpriseException
import com.primeos.mdm.organization.Organization
import com.primeos.mdm.organization.OrganizationNotFoundException
import com.primeos.mdm.organization.OrganizationRepository
import com.primeos.mdm.policy.Policy
import com.primeos.mdm.policy.PolicyNotFoundException
import com.primeos.mdm.policy.PolicyRepository
import com.primeos.mdm.policy.PolicySyncResult
import com.primeos.mdm.policy.PolicyTranslationService
import com.primeos.mdm.policy.translator.CustomDpcPolicyPayload
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import java.util.Optional
import java.util.UUID

class GmsEnrollmentTokenServiceTest {

    private val androidManagementService = mock(AndroidManagementService::class.java)
    private val organizationRepository = mock(OrganizationRepository::class.java)
    private val gmsEnterpriseRepository = mock(GmsEnterpriseRepository::class.java)
    private val policyRepository = mock(PolicyRepository::class.java)
    private val policyTranslationService = mock(PolicyTranslationService::class.java)
    private val enrollmentTokenRepository = mock(EnrollmentTokenRepository::class.java)

    private val service = GmsEnrollmentTokenService(
        androidManagementService,
        organizationRepository,
        gmsEnterpriseRepository,
        policyRepository,
        policyTranslationService,
        enrollmentTokenRepository,
        mock(AdminAccessGuard::class.java),
    )

    private val organizationId = UUID.randomUUID()
    private val organization = Organization(name = "Acme", slug = "acme").apply { id = organizationId }
    private val gmsEnterprise = GmsEnterprise(
        organization = organization,
        enterpriseName = "enterprises/LC00abc123",
        gcpProjectId = "test-project",
        serviceAccountSecretRef = "secrets/android-management-sa.json",
    )
    private val policyId = UUID.randomUUID()
    private val policy = Policy(organization = organization, name = "Default", definition = "{}").apply { id = policyId }

    @BeforeEach
    fun setUp() {
        given(organizationRepository.findById(organizationId)).willReturn(Optional.of(organization))
    }

    @Test
    fun `resolves the internal policy, syncs it, and issues a token with the computed gmsPolicyName`() {
        given(gmsEnterpriseRepository.findByOrganizationId(organizationId)).willReturn(gmsEnterprise)
        given(policyRepository.findById(policyId)).willReturn(Optional.of(policy))
        given(policyTranslationService.syncPolicy(policyId)).willReturn(
            PolicySyncResult(
                customDpcPayload = CustomDpcPolicyPayload(),
                gmsPolicyName = "enterprises/LC00abc123/policies/$policyId",
            )
        )
        given(
            androidManagementService.createEnrollmentToken(
                enterpriseName = eq("enterprises/LC00abc123"),
                policyName = eq("enterprises/LC00abc123/policies/$policyId"),
                oneTimeOnly = eq(true),
                allowPersonalUsage = eq("PERSONAL_USAGE_DISALLOWED"),
                durationSeconds = eq(3600L),
            )
        ).willReturn(
            GoogleEnrollmentToken()
                .setName("enterprises/LC00abc123/enrollmentTokens/xyz")
                .setValue("RAWTOKENVALUE")
                .setQrCode("""{"android.app.extra.PROVISIONING_MODE":true}""")
                .setExpirationTimestamp("2026-08-21T10:00:00.000Z")
        )
        given(enrollmentTokenRepository.save(any())).willAnswer { it.arguments[0] }

        val result = service.issueGmsEnrollmentToken(
            organizationId,
            GmsEnrollmentTokenRequest(policyId = policyId, oneTimeOnly = true, durationSeconds = 3600L),
        )

        assertEquals("RAWTOKENVALUE", result.tokenValue)
        assertEquals(DeviceType.GMS, result.deviceType)
        assertEquals(1, result.maxUses)
        assertEquals(policyId, result.defaultPolicy?.id)
    }

    @Test
    fun `rejects an unknown organization`() {
        val unknownId = UUID.randomUUID()
        given(organizationRepository.findById(unknownId)).willReturn(Optional.empty())

        assertThrows(OrganizationNotFoundException::class.java) {
            service.issueGmsEnrollmentToken(unknownId, GmsEnrollmentTokenRequest(policyId = policyId))
        }
    }

    @Test
    fun `rejects an organization with no GMS enterprise yet`() {
        given(gmsEnterpriseRepository.findByOrganizationId(organizationId)).willReturn(null)

        assertThrows(NoGmsEnterpriseException::class.java) {
            service.issueGmsEnrollmentToken(organizationId, GmsEnrollmentTokenRequest(policyId = policyId))
        }
    }

    @Test
    fun `rejects an unknown policy`() {
        given(gmsEnterpriseRepository.findByOrganizationId(organizationId)).willReturn(gmsEnterprise)
        given(policyRepository.findById(policyId)).willReturn(Optional.empty())

        assertThrows(PolicyNotFoundException::class.java) {
            service.issueGmsEnrollmentToken(organizationId, GmsEnrollmentTokenRequest(policyId = policyId))
        }
    }

    @Test
    fun `rejects a policy that belongs to a different organization`() {
        val otherOrganization = Organization(name = "Other", slug = "other").apply { id = UUID.randomUUID() }
        val otherPolicy = Policy(organization = otherOrganization, name = "Other", definition = "{}").apply { id = policyId }
        given(gmsEnterpriseRepository.findByOrganizationId(organizationId)).willReturn(gmsEnterprise)
        given(policyRepository.findById(policyId)).willReturn(Optional.of(otherPolicy))

        assertThrows(GmsPolicyOrganizationMismatchException::class.java) {
            service.issueGmsEnrollmentToken(organizationId, GmsEnrollmentTokenRequest(policyId = policyId))
        }
    }
}
