package com.primeos.mdm.admin

import java.util.UUID

data class LoginRequest(val email: String, val password: String)

data class LoginResponse(
    val token: String,
    val adminUserId: UUID,
    val role: AdminRole,
    val organizationId: UUID?,
    val expiresInSeconds: Long,
)
