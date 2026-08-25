package com.primeos.mdm.dpc

import com.primeos.mdm.device.Device
import com.primeos.mdm.device.DeviceCredentialService
import com.primeos.mdm.device.DeviceRepository
import com.primeos.mdm.device.DeviceType
import com.primeos.mdm.enrollment.EnrollmentToken
import com.primeos.mdm.enrollment.EnrollmentTokenRepository
import com.primeos.mdm.enrollment.EnrollmentTokenStatus
import com.primeos.mdm.enrollment.InvalidEnrollmentTokenException
import com.primeos.mdm.organization.Organization
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import java.time.Instant
import java.util.Optional
import java.util.UUID

class DpcEnrollmentServiceTest {

    private val enrollmentTokenRepository = mock(EnrollmentTokenRepository::class.java)
    private val deviceRepository = mock(DeviceRepository::class.java)

    private val service = DpcEnrollmentService(
        enrollmentTokenRepository,
        deviceRepository,
        DeviceCredentialService(),
        60L,
    )

    private val organization = Organization(name = "Acme", slug = "acme")

    private fun activeToken(maxUses: Int = 1, usedCount: Int = 0) = EnrollmentToken(
        organization = organization,
        deviceType = DeviceType.NON_GMS,
        tokenValue = "tok-abc123",
        maxUses = maxUses,
        usedCount = usedCount,
    )

    @Test
    fun `enrolls a device against a single-use token and consumes it`() {
        val token = activeToken()
        given(enrollmentTokenRepository.findByTokenValue("tok-abc123")).willReturn(token)
        given(deviceRepository.save(any())).willAnswer { (it.arguments[0] as Device).apply { id = UUID.randomUUID() } }

        val response = service.enroll(DpcEnrollRequest("tok-abc123"))

        assert(response.deviceApiKey.isNotBlank())
        assertEquals(60L, response.checkInIntervalSeconds)
        assertEquals(1, token.usedCount)
        assertEquals(EnrollmentTokenStatus.CONSUMED, token.status)
    }

    @Test
    fun `enrolling against a multi-use token does not consume it or set claimedByDevice`() {
        val token = activeToken(maxUses = 5, usedCount = 1)
        given(enrollmentTokenRepository.findByTokenValue("tok-abc123")).willReturn(token)
        given(deviceRepository.save(any())).willAnswer { (it.arguments[0] as Device).apply { id = UUID.randomUUID() } }

        service.enroll(DpcEnrollRequest("tok-abc123"))

        assertEquals(2, token.usedCount)
        assertEquals(EnrollmentTokenStatus.ACTIVE, token.status)
        assertNull(token.claimedByDevice)
    }

    @Test
    fun `rejects an unknown token`() {
        given(enrollmentTokenRepository.findByTokenValue("nope")).willReturn(null)

        assertThrows(InvalidEnrollmentTokenException::class.java) {
            service.enroll(DpcEnrollRequest("nope"))
        }
    }

    @Test
    fun `rejects a GMS-typed token`() {
        val token = EnrollmentToken(organization = organization, deviceType = DeviceType.GMS, tokenValue = "tok-gms")
        given(enrollmentTokenRepository.findByTokenValue("tok-gms")).willReturn(token)

        assertThrows(InvalidEnrollmentTokenException::class.java) {
            service.enroll(DpcEnrollRequest("tok-gms"))
        }
    }

    @Test
    fun `rejects a revoked token`() {
        val token = activeToken().apply { status = EnrollmentTokenStatus.REVOKED }
        given(enrollmentTokenRepository.findByTokenValue("tok-abc123")).willReturn(token)

        assertThrows(InvalidEnrollmentTokenException::class.java) {
            service.enroll(DpcEnrollRequest("tok-abc123"))
        }
    }

    @Test
    fun `rejects an expired token`() {
        val token = activeToken().apply { expiresAt = Instant.now().minusSeconds(60) }
        given(enrollmentTokenRepository.findByTokenValue("tok-abc123")).willReturn(token)

        assertThrows(InvalidEnrollmentTokenException::class.java) {
            service.enroll(DpcEnrollRequest("tok-abc123"))
        }
    }

    @Test
    fun `rejects a token that already reached max uses`() {
        val token = activeToken(maxUses = 1, usedCount = 1)
        given(enrollmentTokenRepository.findByTokenValue("tok-abc123")).willReturn(token)

        assertThrows(InvalidEnrollmentTokenException::class.java) {
            service.enroll(DpcEnrollRequest("tok-abc123"))
        }
    }
}
