package com.primeos.mdm.admin

import org.springframework.beans.factory.annotation.Value
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class AuthService(
    private val adminUserRepository: AdminUserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtService: JwtService,
    @Value("\${mdm.auth.jwt-expiration-minutes:480}")
    private val expirationMinutes: Long,
) {

    @Transactional
    fun login(request: LoginRequest): LoginResponse {
        val adminUser = adminUserRepository.findByEmail(request.email) ?: throw InvalidCredentialsException()

        // Password check happens before the isActive check (both throwing
        // the same generic exception) so a deactivated account doesn't
        // short-circuit past bcrypt's characteristic delay - that timing
        // difference would otherwise let an outside observer distinguish
        // "wrong password" from "deactivated" without ever seeing a
        // different message.
        if (!passwordEncoder.matches(request.password, adminUser.passwordHash)) {
            throw InvalidCredentialsException()
        }
        if (!adminUser.isActive) {
            throw InvalidCredentialsException()
        }

        adminUser.lastLoginAt = Instant.now()
        adminUserRepository.save(adminUser)

        return LoginResponse(
            token = jwtService.issueToken(adminUser),
            adminUserId = adminUser.id!!,
            role = adminUser.role,
            organizationId = adminUser.organization?.id,
            expiresInSeconds = expirationMinutes * 60,
        )
    }
}
