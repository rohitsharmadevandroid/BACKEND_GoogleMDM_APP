package com.primeos.mdm.enrollment

import com.primeos.mdm.admin.AdminAccessGuard
import com.primeos.mdm.device.DeviceType
import com.primeos.mdm.organization.Organization
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import java.util.Optional
import java.util.UUID

class EnrollmentTokenRevocationServiceTest {

    private val enrollmentTokenRepository = mock(EnrollmentTokenRepository::class.java)
    private val adminAccessGuard = mock(AdminAccessGuard::class.java)
    private val service = EnrollmentTokenRevocationService(enrollmentTokenRepository, adminAccessGuard)

    private val organizationId = UUID.randomUUID()
    private val organization = Organization(name = "Acme", slug = "acme").apply { id = organizationId }

    @Test
    fun `revokes an active token`() {
        val tokenId = UUID.randomUUID()
        val token = EnrollmentToken(organization = organization, deviceType = DeviceType.NON_GMS, tokenValue = "tok-1")
            .apply { id = tokenId }
        given(enrollmentTokenRepository.findById(tokenId)).willReturn(Optional.of(token))
        given(enrollmentTokenRepository.save(any())).willAnswer { it.arguments[0] }

        val result = service.revoke(tokenId)

        assertEquals(EnrollmentTokenStatus.REVOKED, result.status)
        verify(adminAccessGuard).requireOrganizationAccess(organizationId)
    }

    @Test
    fun `revoking an already-consumed token is idempotent, not an error`() {
        val tokenId = UUID.randomUUID()
        val token = EnrollmentToken(organization = organization, deviceType = DeviceType.NON_GMS, tokenValue = "tok-1")
            .apply { id = tokenId; status = EnrollmentTokenStatus.CONSUMED }
        given(enrollmentTokenRepository.findById(tokenId)).willReturn(Optional.of(token))
        given(enrollmentTokenRepository.save(any())).willAnswer { it.arguments[0] }

        val result = service.revoke(tokenId)

        assertEquals(EnrollmentTokenStatus.REVOKED, result.status)
    }

    @Test
    fun `rejects an unknown token`() {
        val unknownId = UUID.randomUUID()
        given(enrollmentTokenRepository.findById(unknownId)).willReturn(Optional.empty())

        assertThrows(EnrollmentTokenNotFoundException::class.java) {
            service.revoke(unknownId)
        }
    }
}
