package com.primeos.mdm.admin

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import java.util.UUID

class AdminAccessGuardTest {

    private val guard = AdminAccessGuard()

    @AfterEach
    fun clearSecurityContext() {
        SecurityContextHolder.clearContext()
    }

    private fun authenticateAs(claims: AdminTokenClaims) {
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(claims, null, listOf(SimpleGrantedAuthority("ROLE_${claims.role.name}")))
    }

    @Test
    fun `a super-admin (null organizationId) can access any organization`() {
        authenticateAs(AdminTokenClaims(adminUserId = UUID.randomUUID(), role = AdminRole.SUPER_ADMIN, organizationId = null))

        assertDoesNotThrow {
            guard.requireOrganizationAccess(UUID.randomUUID())
            guard.requireOrganizationAccess(UUID.randomUUID())
        }
    }

    @Test
    fun `an org-scoped admin can access their own organization`() {
        val organizationId = UUID.randomUUID()
        authenticateAs(AdminTokenClaims(adminUserId = UUID.randomUUID(), role = AdminRole.ORG_ADMIN, organizationId = organizationId))

        assertDoesNotThrow { guard.requireOrganizationAccess(organizationId) }
    }

    @Test
    fun `an org-scoped admin is rejected from a different organization`() {
        val theirOrg = UUID.randomUUID()
        val otherOrg = UUID.randomUUID()
        authenticateAs(AdminTokenClaims(adminUserId = UUID.randomUUID(), role = AdminRole.ORG_ADMIN, organizationId = theirOrg))

        assertThrows(OrganizationAccessDeniedException::class.java) {
            guard.requireOrganizationAccess(otherOrg)
        }
    }

    @Test
    fun `an org-scoped viewer is rejected from a different organization the same way as an admin`() {
        val theirOrg = UUID.randomUUID()
        val otherOrg = UUID.randomUUID()
        authenticateAs(AdminTokenClaims(adminUserId = UUID.randomUUID(), role = AdminRole.ORG_VIEWER, organizationId = theirOrg))

        assertThrows(OrganizationAccessDeniedException::class.java) {
            guard.requireOrganizationAccess(otherOrg)
        }
    }

    @Test
    fun `currentClaims fails clearly when nothing is authenticated`() {
        assertThrows(IllegalStateException::class.java) {
            guard.currentClaims()
        }
    }
}
