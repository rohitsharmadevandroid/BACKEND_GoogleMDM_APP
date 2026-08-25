package com.primeos.mdm.admin

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

// Lets an ORG_ADMIN/ORG_VIEWER see their own org's admin team, guarded by
// AdminAccessGuard rather than a role restriction - separate from
// AdminUserController, whose endpoints are all SUPER_ADMIN-only.
@RestController
@RequestMapping("/api/organizations/{organizationId}/admin-users")
class OrgScopedAdminUserController(
    private val adminUserService: AdminUserService,
) {

    @GetMapping
    fun list(@PathVariable organizationId: UUID): List<AdminUserResponse> =
        adminUserService.listByOrganization(organizationId)
}
