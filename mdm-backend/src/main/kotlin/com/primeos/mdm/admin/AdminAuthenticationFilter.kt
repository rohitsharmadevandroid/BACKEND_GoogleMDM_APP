package com.primeos.mdm.admin

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.OncePerRequestFilter

// Resolves the admin JWT from "Authorization: Bearer <token>" into a real
// Spring Security Authentication (principal = AdminTokenClaims), so
// SecurityConfig's role-based authorizeHttpRequests rules work the normal
// Spring Security way. Unlike DeviceAuthenticationFilter (which just
// stashes a request attribute, since the DPC endpoints have no role
// concept), this integrates with Spring Security proper because role-based
// access control (SUPER_ADMIN / ORG_ADMIN / ORG_VIEWER) is exactly what
// that machinery exists for.
//
// A missing or invalid token here just leaves the request unauthenticated -
// this filter never itself rejects anything; SecurityConfig's
// authorizeHttpRequests rules do that.
class AdminAuthenticationFilter(
    private val jwtService: JwtService,
) : OncePerRequestFilter() {

    override fun shouldNotFilter(request: HttpServletRequest): Boolean =
        request.requestURI.startsWith("/api/dpc/") || request.requestURI == "/api/auth/login"

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, filterChain: FilterChain) {
        val token = request.getHeader("Authorization")
            ?.takeIf { it.startsWith("Bearer ") }
            ?.removePrefix("Bearer ")

        if (token != null) {
            try {
                val claims = jwtService.parse(token)
                val authorities = listOf(SimpleGrantedAuthority("ROLE_${claims.role.name}"))
                SecurityContextHolder.getContext().authentication =
                    UsernamePasswordAuthenticationToken(claims, null, authorities)
            } catch (ex: Exception) {
                // Invalid/expired/tampered token - leave unauthenticated,
                // authorizeHttpRequests below rejects it with 401/403.
            }
        }

        filterChain.doFilter(request, response)
    }
}
