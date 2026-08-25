package com.primeos.mdm.enrollment

import com.primeos.mdm.admin.AdminAccessGuard
import com.primeos.mdm.device.DeviceType
import com.primeos.mdm.organization.Organization
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock
import java.util.UUID

class EnrollmentTokenQueryServiceTest {

    private val enrollmentTokenRepository = mock(EnrollmentTokenRepository::class.java)
    private val service = EnrollmentTokenQueryService(enrollmentTokenRepository, mock(AdminAccessGuard::class.java))

    @Test
    fun `maps both GMS and non-GMS tokens for an organization`() {
        val organizationId = UUID.randomUUID()
        val organization = Organization(name = "Acme", slug = "acme").apply { id = organizationId }
        val gmsToken = EnrollmentToken(organization = organization, deviceType = DeviceType.GMS, tokenValue = "gms-tok")
            .apply { id = UUID.randomUUID() }
        val nonGmsToken = EnrollmentToken(organization = organization, deviceType = DeviceType.NON_GMS, tokenValue = "non-gms-tok")
            .apply { id = UUID.randomUUID() }
        given(enrollmentTokenRepository.findByOrganizationId(organizationId)).willReturn(listOf(gmsToken, nonGmsToken))

        val summaries = service.listByOrganization(organizationId)

        assertEquals(2, summaries.size)
        assertEquals(setOf(DeviceType.GMS, DeviceType.NON_GMS), summaries.map { it.deviceType }.toSet())
    }
}
