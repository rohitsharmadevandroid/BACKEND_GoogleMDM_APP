package com.primeos.mdm.enrollment

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.primeos.mdm.admin.AdminAccessGuard
import com.primeos.mdm.device.DeviceType
import com.primeos.mdm.organization.Organization
import com.primeos.mdm.organization.OrganizationNotFoundException
import com.primeos.mdm.organization.OrganizationRepository
import com.primeos.mdm.policy.Policy
import com.primeos.mdm.policy.PolicyNotFoundException
import com.primeos.mdm.policy.PolicyRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import java.util.Optional
import java.util.UUID

class NonGmsEnrollmentTokenServiceTest {

    private val organizationRepository = mock(OrganizationRepository::class.java)
    private val policyRepository = mock(PolicyRepository::class.java)
    private val enrollmentTokenRepository = mock(EnrollmentTokenRepository::class.java)

    // Blank config everywhere -> isConfigured() false -> issueToken always
    // falls back to the placeholder QR in these tests, matching current
    // real-world behavior until the Android side's facts are known.
    private val service = NonGmsEnrollmentTokenService(
        organizationRepository,
        policyRepository,
        enrollmentTokenRepository,
        NonGmsProvisioningQrCodeBuilder(jacksonObjectMapper(), "", "", ""),
        mock(AdminAccessGuard::class.java),
    )

    private val organizationId = UUID.randomUUID()
    private val organization = Organization(name = "Acme", slug = "acme").apply { id = organizationId }

    @Test
    fun `issues a token with a unique value and embeds it in qrCodeData`() {
        given(organizationRepository.findById(organizationId)).willReturn(Optional.of(organization))
        given(enrollmentTokenRepository.save(any())).willAnswer { it.arguments[0] }

        val token = service.issueToken(organizationId, NonGmsEnrollmentTokenRequest())

        assertEquals(DeviceType.NON_GMS, token.deviceType)
        assertEquals(1, token.maxUses)
        assert(token.qrCodeData?.contains(token.tokenValue) == true)
    }

    @Test
    fun `attaches the requested default policy when it exists`() {
        val policyId = UUID.randomUUID()
        given(organizationRepository.findById(organizationId)).willReturn(Optional.of(organization))
        given(policyRepository.findById(policyId)).willReturn(
            Optional.of(Policy(organization = organization, name = "Kiosk", definition = "{}").apply { id = policyId })
        )
        given(enrollmentTokenRepository.save(any())).willAnswer { it.arguments[0] }

        val token = service.issueToken(organizationId, NonGmsEnrollmentTokenRequest(defaultPolicyId = policyId, maxUses = 10))

        assertEquals(policyId, token.defaultPolicy?.id)
        assertEquals(10, token.maxUses)
    }

    @Test
    fun `rejects an unknown organization`() {
        val unknownId = UUID.randomUUID()
        given(organizationRepository.findById(unknownId)).willReturn(Optional.empty())

        assertThrows(OrganizationNotFoundException::class.java) {
            service.issueToken(unknownId, NonGmsEnrollmentTokenRequest())
        }
    }

    @Test
    fun `rejects an unknown default policy`() {
        val policyId = UUID.randomUUID()
        given(organizationRepository.findById(organizationId)).willReturn(Optional.of(organization))
        given(policyRepository.findById(policyId)).willReturn(Optional.empty())

        assertThrows(PolicyNotFoundException::class.java) {
            service.issueToken(organizationId, NonGmsEnrollmentTokenRequest(defaultPolicyId = policyId))
        }
    }
}
