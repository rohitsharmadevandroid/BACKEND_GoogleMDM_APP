package com.primeos.mdm.admin

import com.primeos.mdm.organization.Organization
import io.jsonwebtoken.ExpiredJwtException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.util.UUID

class JwtServiceTest {

    private val secret = "test-only-secret-that-is-at-least-32-bytes-long"

    @Test
    fun `round-trips an admin user's identity, role, and org through issue and parse`() {
        val jwtService = JwtService(secret, 480)
        val organizationId = UUID.randomUUID()
        val adminUserId = UUID.randomUUID()
        val adminUser = AdminUser(
            organization = Organization(name = "Acme", slug = "acme").apply { id = organizationId },
            email = "admin@acme.test",
            passwordHash = "irrelevant-here",
            role = AdminRole.ORG_ADMIN,
        ).apply { id = adminUserId }

        val token = jwtService.issueToken(adminUser)
        val claims = jwtService.parse(token)

        assertEquals(adminUserId, claims.adminUserId)
        assertEquals(AdminRole.ORG_ADMIN, claims.role)
        assertEquals(organizationId, claims.organizationId)
    }

    @Test
    fun `a platform super-admin with no organization round-trips a null organizationId`() {
        val jwtService = JwtService(secret, 480)
        val adminUser = AdminUser(
            organization = null,
            email = "super@primeos.test",
            passwordHash = "irrelevant-here",
            role = AdminRole.SUPER_ADMIN,
        ).apply { id = UUID.randomUUID() }

        val claims = jwtService.parse(jwtService.issueToken(adminUser))

        assertNull(claims.organizationId)
        assertEquals(AdminRole.SUPER_ADMIN, claims.role)
    }

    @Test
    fun `rejects an already-expired token`() {
        val jwtService = JwtService(secret, expirationMinutes = 0)
        val adminUser = AdminUser(
            organization = null,
            email = "super@primeos.test",
            passwordHash = "irrelevant-here",
            role = AdminRole.SUPER_ADMIN,
        ).apply { id = UUID.randomUUID() }
        val token = jwtService.issueToken(adminUser)

        Thread.sleep(50)

        assertThrows(ExpiredJwtException::class.java) {
            jwtService.parse(token)
        }
    }

    @Test
    fun `rejects a token signed with a different secret`() {
        val issuer = JwtService(secret, 480)
        val verifier = JwtService("a-completely-different-secret-that-is-also-32-bytes", 480)
        val adminUser = AdminUser(
            organization = null,
            email = "super@primeos.test",
            passwordHash = "irrelevant-here",
            role = AdminRole.SUPER_ADMIN,
        ).apply { id = UUID.randomUUID() }
        val token = issuer.issueToken(adminUser)

        assertThrows(io.jsonwebtoken.security.SignatureException::class.java) {
            verifier.parse(token)
        }
    }
}
