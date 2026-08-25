package com.primeos.mdm.admin

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import java.util.UUID

class AuthServiceTest {

    private val adminUserRepository = mock(AdminUserRepository::class.java)
    private val jwtService = mock(JwtService::class.java)
    private val passwordEncoder = BCryptPasswordEncoder()

    private val service = AuthService(adminUserRepository, passwordEncoder, jwtService, 480L)

    @Test
    fun `issues a token and updates lastLoginAt on a correct password`() {
        val adminUser = AdminUser(
            organization = null,
            email = "super@primeos.test",
            passwordHash = passwordEncoder.encode("correct-password"),
            role = AdminRole.SUPER_ADMIN,
        ).apply { id = UUID.randomUUID() }
        given(adminUserRepository.findByEmail("super@primeos.test")).willReturn(adminUser)
        given(jwtService.issueToken(adminUser)).willReturn("signed.jwt.token")
        given(adminUserRepository.save(any())).willAnswer { it.arguments[0] }

        val response = service.login(LoginRequest(email = "super@primeos.test", password = "correct-password"))

        assertEquals("signed.jwt.token", response.token)
        assertEquals(AdminRole.SUPER_ADMIN, response.role)
        assertNotNull(adminUser.lastLoginAt)
    }

    @Test
    fun `rejects a wrong password without revealing that the email exists`() {
        val adminUser = AdminUser(
            organization = null,
            email = "super@primeos.test",
            passwordHash = passwordEncoder.encode("correct-password"),
            role = AdminRole.SUPER_ADMIN,
        ).apply { id = UUID.randomUUID() }
        given(adminUserRepository.findByEmail("super@primeos.test")).willReturn(adminUser)

        assertThrows(InvalidCredentialsException::class.java) {
            service.login(LoginRequest(email = "super@primeos.test", password = "wrong-password"))
        }
    }

    @Test
    fun `rejects an unknown email with the same exception as a wrong password`() {
        given(adminUserRepository.findByEmail("nobody@primeos.test")).willReturn(null)

        assertThrows(InvalidCredentialsException::class.java) {
            service.login(LoginRequest(email = "nobody@primeos.test", password = "anything"))
        }
    }

    @Test
    fun `rejects a deactivated admin even with the correct password`() {
        val adminUser = AdminUser(
            organization = null,
            email = "deactivated@primeos.test",
            passwordHash = passwordEncoder.encode("correct-password"),
            role = AdminRole.SUPER_ADMIN,
            isActive = false,
        ).apply { id = UUID.randomUUID() }
        given(adminUserRepository.findByEmail("deactivated@primeos.test")).willReturn(adminUser)

        assertThrows(InvalidCredentialsException::class.java) {
            service.login(LoginRequest(email = "deactivated@primeos.test", password = "correct-password"))
        }
    }
}
