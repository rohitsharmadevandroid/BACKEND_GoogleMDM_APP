package com.primeos.mdm.admin

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.util.Date
import java.util.UUID
import javax.crypto.SecretKey

data class AdminTokenClaims(val adminUserId: UUID, val role: AdminRole, val organizationId: UUID?)

@Service
class JwtService(
    @Value("\${mdm.auth.jwt-secret}")
    secret: String,
    @Value("\${mdm.auth.jwt-expiration-minutes:480}")
    private val expirationMinutes: Long,
) {

    // HS256 requires at least a 256-bit key; the dev default secret in
    // application.yml is long enough, but a short override here would
    // throw WeakKeyException at startup rather than silently produce
    // insecure tokens.
    private val key: SecretKey = Keys.hmacShaKeyFor(secret.toByteArray())

    fun issueToken(adminUser: AdminUser): String {
        val now = Date()
        val expiry = Date(now.time + expirationMinutes * 60_000)

        return Jwts.builder()
            .subject(adminUser.id.toString())
            .claim("role", adminUser.role.name)
            .claim("organizationId", adminUser.organization?.id?.toString())
            .issuedAt(now)
            .expiration(expiry)
            .signWith(key)
            .compact()
    }

    // Throws (ExpiredJwtException, SignatureException, etc.) on any
    // invalid/expired/tampered token - callers treat any exception here as
    // "unauthenticated", not as distinct failure modes to handle specially.
    fun parse(token: String): AdminTokenClaims {
        val claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).payload
        return AdminTokenClaims(
            adminUserId = UUID.fromString(claims.subject),
            role = AdminRole.valueOf(claims.get("role", String::class.java)),
            organizationId = claims.get("organizationId", String::class.java)?.let { UUID.fromString(it) },
        )
    }
}
