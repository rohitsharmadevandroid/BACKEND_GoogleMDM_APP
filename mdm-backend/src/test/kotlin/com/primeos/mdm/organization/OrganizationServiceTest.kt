package com.primeos.mdm.organization

import com.primeos.mdm.admin.AdminAccessGuard
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import java.util.Optional
import java.util.UUID

class OrganizationServiceTest {

    private val organizationRepository = mock(OrganizationRepository::class.java)
    private val service = OrganizationService(organizationRepository, mock(AdminAccessGuard::class.java))

    @Test
    fun `creates an organization with a unique slug`() {
        given(organizationRepository.findBySlug("acme")).willReturn(null)
        given(organizationRepository.save(any())).willAnswer { it.arguments[0] }

        val organization = service.create(CreateOrganizationRequest(name = "Acme", slug = "acme"))

        assertEquals("Acme", organization.name)
        assertEquals("acme", organization.slug)
    }

    @Test
    fun `rejects a duplicate slug`() {
        given(organizationRepository.findBySlug("acme")).willReturn(
            Organization(name = "Existing", slug = "acme")
        )

        assertThrows(DuplicateSlugException::class.java) {
            service.create(CreateOrganizationRequest(name = "Acme", slug = "acme"))
        }
    }

    @Test
    fun `rejects an unknown organization id`() {
        val unknownId = UUID.randomUUID()
        given(organizationRepository.findById(unknownId)).willReturn(Optional.empty())

        assertThrows(OrganizationNotFoundException::class.java) {
            service.get(unknownId)
        }
    }
}
