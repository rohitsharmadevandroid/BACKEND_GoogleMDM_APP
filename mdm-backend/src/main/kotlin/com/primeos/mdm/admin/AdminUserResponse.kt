package com.primeos.mdm.admin

import java.util.UUID

data class AdminUserResponse(
    val id: UUID,
    val email: String,
    val role: AdminRole,
    val organizationId: UUID?,
    val isActive: Boolean,
)
