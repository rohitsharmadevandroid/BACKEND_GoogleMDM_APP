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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock
import java.util.Optional
import java.util.UUID

class GmsEnrollmentTokenServiceTest {

    private val androidManagementService = mock(AndroidManagementService::class.java)
    private val organizationRepository = mock(OrganizationRepository::class.java)
    private val gmsEnterpriseRepository = mock(GmsEnterpriseRepository::class.java)
    private val enrollmentTokenRepository = mock(EnrollmentTokenRepository::class.java)

    private val service = GmsEnrollmentTokenService(
        androidManagementService,
        organizationRepository,
        gmsEnterpriseRepository,
        enrollmentTokenRepository,
        mock(AdminAccessGuard::class.java),
    )

    private val organizationId = UUID.randomUUID()
    private val organization = Organization(name = "Acme", slug = "acme")
    private val gmsEnterprise = GmsEnterprise(
        organization = organization,
        enterpriseName = "enterprises/LC00abc123",
        gcpProjectId = "test-project",
        serviceAccountSecretRef = "secrets/android-management-sa.json",
    )

    @BeforeEach
    fun setUp() {
        given(organizationRepository.findById(organizationId)).willReturn(Optional.of(organization))
    }

    @Test
    fun `issues a GMS enrollment token and persists it`() {
        given(gmsEnterpriseRepository.findByOrganizationId(organizationId)).willReturn(gmsEnterprise)
        given(
            androidManagementService.createEnrollmentToken(
                enterpriseName = "enterprises/LC00abc123",
                policyName = "enterprises/LC00abc123/policies/default",
                oneTimeOnly = true,
                allowPersonalUsage = "PERSONAL_USAGE_DISALLOWED",
                durationSeconds = 3600L,
            )
        ).willReturn(
            GoogleEnrollmentToken()
                .setName("enterprises/LC00abc123/enrollmentTokens/xyz")
                .setValue("RAWTOKENVALUE")
                .setQrCode("""{"android.app.extra.PROVISIONING_MODE":true}""")
                .setExpirationTimestamp("2026-08-21T10:00:00.000Z")
        )
        given(enrollmentTokenRepository.save(any(EnrollmentToken::class.java))).willAnswer { it.arguments[0] }

        val result = service.issueGmsEnrollmentToken(
            organizationId,
            GmsEnrollmentTokenRequest(
                policyName = "enterprises/LC00abc123/policies/default",
                oneTimeOnly = true,
                allowPersonalUsage = "PERSONAL_USAGE_DISALLOWED",
                durationSeconds = 3600L,
            ),
        )

        assertEquals("RAWTOKENVALUE", result.tokenValue)
        assertEquals(DeviceType.GMS, result.deviceType)
        assertEquals(1, result.maxUses)
    }

    @Test
    fun `rejects an unknown organization`() {
        val unknownId = UUID.randomUUID()
        given(organizationRepository.findById(unknownId)).willReturn(Optional.empty())

        assertThrows(OrganizationNotFoundException::class.java) {
            service.issueGmsEnrollmentToken(unknownId, GmsEnrollmentTokenRequest(policyName = "irrelevant"))
        }
    }

    @Test
    fun `rejects an organization with no GMS enterprise yet`() {
        given(gmsEnterpriseRepository.findByOrganizationId(organizationId)).willReturn(null)

        assertThrows(NoGmsEnterpriseException::class.java) {
            service.issueGmsEnrollmentToken(organizationId, GmsEnrollmentTokenRequest(policyName = "irrelevant"))
        }
    }
}
