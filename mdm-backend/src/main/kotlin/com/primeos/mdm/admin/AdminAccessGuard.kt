package com.primeos.mdm.admin

import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import java.util.UUID

// Central enforcement point for per-organization data isolation. Role
// checks in SecurityConfig gate WHAT KIND of action is allowed; this gates
// WHICH organization's data - a SUPER_ADMIN (organizationId == null in
// their token) can touch any organization, everyone else only their own.
// Every org-scoped service method calls this before touching data,
// matching the established pattern of services owning their own
// invariants (see DeviceService's CHECK-constraint validation).
@Component
class AdminAccessGuard {

    fun currentClaims(): AdminTokenClaims =
        SecurityContextHolder.getContext().authentication?.principal as? AdminTokenClaims
            ?: error("No authenticated admin in the security context - this should be unreachable, since SecurityConfig requires authentication before any org-scoped controller runs")

    fun requireOrganizationAccess(organizationId: UUID) {
        val claims = currentClaims()
        if (claims.organizationId != null && claims.organizationId != organizationId) {
            throw OrganizationAccessDeniedException(organizationId)
        }
    }
}
