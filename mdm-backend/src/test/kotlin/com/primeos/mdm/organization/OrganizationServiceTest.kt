package com.primeos.mdm.organization

import com.primeos.mdm.admin.AdminAccessGuard
import com.primeos.mdm.admin.AdminUser
import com.primeos.mdm.admin.AdminRole
import com.primeos.mdm.admin.AdminUserRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import java.util.Optional
import java.util.UUID

class OrganizationServiceTest {

    private val organizationRepository = mock(OrganizationRepository::class.java)
    private val adminUserRepository = mock(AdminUserRepository::class.java)
    private val service = OrganizationService(organizationRepository, adminUserRepository, mock(AdminAccessGuard::class.java))

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

    @Test
    fun `deletes an organization with no admin users`() {
        val organizationId = UUID.randomUUID()
        val organization = Organization(name = "Acme", slug = "acme").apply { id = organizationId }
        given(organizationRepository.findById(organizationId)).willReturn(Optional.of(organization))
        given(adminUserRepository.findByOrganizationId(organizationId)).willReturn(emptyList())

        service.delete(organizationId)

        verify(organizationRepository).delete(organization)
    }

    @Test
    fun `refuses to delete an organization that still has admin users`() {
        val organizationId = UUID.randomUUID()
        val organization = Organization(name = "Acme", slug = "acme").apply { id = organizationId }
        given(organizationRepository.findById(organizationId)).willReturn(Optional.of(organization))
        given(adminUserRepository.findByOrganizationId(organizationId)).willReturn(
            listOf(AdminUser(organization = organization, email = "a@acme.com", passwordHash = "hash", role = AdminRole.ORG_ADMIN))
        )

        assertThrows(OrganizationHasAdminUsersException::class.java) {
            service.delete(organizationId)
        }
        verify(organizationRepository, never()).delete(any())
    }

    @Test
    fun `rejects deleting an unknown organization`() {
        val unknownId = UUID.randomUUID()
        given(organizationRepository.findById(unknownId)).willReturn(Optional.empty())

        assertThrows(OrganizationNotFoundException::class.java) {
            service.delete(unknownId)
        }
    }
}
