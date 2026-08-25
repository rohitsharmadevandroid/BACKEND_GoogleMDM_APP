package com.primeos.mdm.admin

import java.util.UUID

data class CreateAdminUserRequest(
    val email: String,
    val password: String,
    val role: AdminRole,
    // null = platform-level super-admin, not tied to any one org
    val organizationId: UUID? = null,
)
