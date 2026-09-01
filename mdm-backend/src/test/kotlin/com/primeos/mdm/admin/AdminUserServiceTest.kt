package com.primeos.mdm.admin

import com.primeos.mdm.organization.Organization
import com.primeos.mdm.organization.OrganizationNotFoundException
import com.primeos.mdm.organization.OrganizationRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import java.util.Optional
import java.util.UUID

class AdminUserServiceTest {

    private val adminUserRepository = mock(AdminUserRepository::class.java)
    private val organizationRepository = mock(OrganizationRepository::class.java)
    private val adminAccessGuard = mock(AdminAccessGuard::class.java)
    private val service = AdminUserService(adminUserRepository, organizationRepository, BCryptPasswordEncoder(), adminAccessGuard)

    @Test
    fun `creates a platform super-admin with no organization and hashes the password`() {
        given(adminUserRepository.findByEmail("super@primeos.test")).willReturn(null)
        given(adminUserRepository.save(any())).willAnswer { (it.arguments[0] as AdminUser).apply { id = UUID.randomUUID() } }

        val response = service.create(
            CreateAdminUserRequest(email = "super@primeos.test", password = "correct-password", role = AdminRole.SUPER_ADMIN)
        )

        assertEquals(AdminRole.SUPER_ADMIN, response.role)
        assertNull(response.organizationId)
    }

    @Test
    fun `creates an org-scoped admin`() {
        val organizationId = UUID.randomUUID()
        val organization = Organization(name = "Acme", slug = "acme").apply { id = organizationId }
        given(adminUserRepository.findByEmail("admin@acme.test")).willReturn(null)
        given(organizationRepository.findById(organizationId)).willReturn(Optional.of(organization))
        given(adminUserRepository.save(any())).willAnswer { (it.arguments[0] as AdminUser).apply { id = UUID.randomUUID() } }

        val response = service.create(
            CreateAdminUserRequest(
                email = "admin@acme.test",
                password = "correct-password",
                role = AdminRole.ORG_ADMIN,
                organizationId = organizationId,
            )
        )

        assertEquals(organizationId, response.organizationId)
    }

    @Test
    fun `rejects a duplicate email`() {
        given(adminUserRepository.findByEmail("super@primeos.test")).willReturn(
            AdminUser(organization = null, email = "super@primeos.test", passwordHash = "x", role = AdminRole.SUPER_ADMIN)
        )

        assertThrows(DuplicateEmailException::class.java) {
            service.create(CreateAdminUserRequest(email = "super@primeos.test", password = "x", role = AdminRole.SUPER_ADMIN))
        }
    }

    @Test
    fun `rejects an ORG_ADMIN with no organization`() {
        given(adminUserRepository.findByEmail("orphan@primeos.test")).willReturn(null)

        assertThrows(AdminUserRoleOrganizationMismatchException::class.java) {
            service.create(CreateAdminUserRequest(email = "orphan@primeos.test", password = "x", role = AdminRole.ORG_ADMIN))
        }
    }

    @Test
    fun `rejects an ORG_VIEWER with no organization`() {
        given(adminUserRepository.findByEmail("orphan@primeos.test")).willReturn(null)

        assertThrows(AdminUserRoleOrganizationMismatchException::class.java) {
            service.create(CreateAdminUserRequest(email = "orphan@primeos.test", password = "x", role = AdminRole.ORG_VIEWER))
        }
    }

    @Test
    fun `rejects a SUPER_ADMIN with an organization`() {
        val organizationId = UUID.randomUUID()
        given(adminUserRepository.findByEmail("scoped-super@primeos.test")).willReturn(null)

        assertThrows(AdminUserRoleOrganizationMismatchException::class.java) {
            service.create(
                CreateAdminUserRequest(
                    email = "scoped-super@primeos.test",
                    password = "x",
                    role = AdminRole.SUPER_ADMIN,
                    organizationId = organizationId,
                )
            )
        }
    }

    @Test
    fun `rejects an unknown organization`() {
        val unknownId = UUID.randomUUID()
        given(adminUserRepository.findByEmail("admin@acme.test")).willReturn(null)
        given(organizationRepository.findById(unknownId)).willReturn(Optional.empty())

        assertThrows(OrganizationNotFoundException::class.java) {
            service.create(
                CreateAdminUserRequest(email = "admin@acme.test", password = "x", role = AdminRole.ORG_ADMIN, organizationId = unknownId)
            )
        }
    }

    @Test
    fun `never stores the raw password`() {
        given(adminUserRepository.findByEmail("super@primeos.test")).willReturn(null)
        given(adminUserRepository.save(any())).willAnswer { (it.arguments[0] as AdminUser).apply { id = UUID.randomUUID() } }

        service.create(CreateAdminUserRequest(email = "super@primeos.test", password = "correct-password", role = AdminRole.SUPER_ADMIN))

        val captor = org.mockito.kotlin.argumentCaptor<AdminUser>()
        org.mockito.Mockito.verify(adminUserRepository).save(captor.capture())
        assertNotEquals("correct-password", captor.firstValue.passwordHash)
    }

    @Test
    fun `listByOrganization checks access before returning the org's admins`() {
        val organizationId = UUID.randomUUID()
        val organization = Organization(name = "Acme", slug = "acme").apply { id = organizationId }
        val admin = AdminUser(organization = organization, email = "a@acme.test", passwordHash = "x", role = AdminRole.ORG_ADMIN)
            .apply { id = UUID.randomUUID() }
        given(adminUserRepository.findByOrganizationId(organizationId)).willReturn(listOf(admin))

        val results = service.listByOrganization(organizationId)

        assertEquals(1, results.size)
        org.mockito.Mockito.verify(adminAccessGuard).requireOrganizationAccess(organizationId)
    }

    @Test
    fun `deactivate flips isActive to false`() {
        val adminUserId = UUID.randomUUID()
        val admin = AdminUser(organization = null, email = "x@primeos.test", passwordHash = "x", role = AdminRole.SUPER_ADMIN)
            .apply { id = adminUserId }
        given(adminUserRepository.findById(adminUserId)).willReturn(Optional.of(admin))
        given(adminUserRepository.save(any())).willAnswer { it.arguments[0] }

        val response = service.deactivate(adminUserId)

        assertEquals(false, response.isActive)
    }

    @Test
    fun `deactivate rejects an unknown admin user`() {
        val unknownId = UUID.randomUUID()
        given(adminUserRepository.findById(unknownId)).willReturn(Optional.empty())

        assertThrows(AdminUserNotFoundException::class.java) {
            service.deactivate(unknownId)
        }
    }
}
