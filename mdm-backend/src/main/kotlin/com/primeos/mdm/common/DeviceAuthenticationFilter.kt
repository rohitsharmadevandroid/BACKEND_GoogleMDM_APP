package com.primeos.mdm.common

import com.primeos.mdm.device.DeviceCredentialService
import com.primeos.mdm.device.DeviceRepository
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.web.filter.OncePerRequestFilter

// A lightweight Bearer-token check for the DPC-facing endpoints
// (/api/dpc/checkin, /api/dpc/commands/**) - deliberately NOT routed
// through Spring Security's authentication machinery, since no admin auth
// exists yet either (see SecurityConfig, which permits everything at that
// layer). This either resolves a Device from the presented key and stores
// it as a request attribute, or rejects with 401 before the controller
// ever runs. /api/dpc/enroll is exempt - the enrollment token itself is
// that endpoint's proof of identity.
//
// Registered explicitly in SecurityConfig via addFilterBefore rather than
// as a @Component, so it isn't ALSO picked up by Spring Boot's generic
// servlet-filter auto-registration and run twice per request.
class DeviceAuthenticationFilter(
    private val deviceRepository: DeviceRepository,
    private val deviceCredentialService: DeviceCredentialService,
) : OncePerRequestFilter() {

    override fun shouldNotFilter(request: HttpServletRequest): Boolean =
        !request.requestURI.startsWith("/api/dpc/") || request.requestURI == "/api/dpc/enroll"

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, filterChain: FilterChain) {
        val rawKey = request.getHeader("Authorization")
            ?.takeIf { it.startsWith("Bearer ") }
            ?.removePrefix("Bearer ")

        val device = rawKey?.let { deviceRepository.findByCredentialHash(deviceCredentialService.hash(it)) }

        if (device == null || device.credentialRevokedAt != null) {
            response.status = HttpServletResponse.SC_UNAUTHORIZED
            return
        }

        request.setAttribute("authenticatedDevice", device)
        filterChain.doFilter(request, response)
    }
}
