package com.primeos.mdm.admin

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

// create/list/deactivate here are restricted to SUPER_ADMIN via path-based
// rules in SecurityConfig, not @PreAuthorize annotations - keeping every
// authorization rule in one file is worth more than saving a few lines.
// The org-scoped list lives in a separate controller (see
// OrgScopedAdminUserController) since it has different, AdminAccessGuard-
// based authorization instead.
@RestController
@RequestMapping("/api/admin-users")
class AdminUserController(
    private val adminUserService: AdminUserService,
) {

    @PostMapping
    fun create(@RequestBody request: CreateAdminUserRequest): AdminUserResponse = adminUserService.create(request)

    @GetMapping
    fun list(): List<AdminUserResponse> = adminUserService.list()

    @PostMapping("/{adminUserId}/deactivate")
    fun deactivate(@PathVariable adminUserId: UUID): AdminUserResponse = adminUserService.deactivate(adminUserId)
}
