package com.primeos.mdm.policy

import java.util.UUID

data class PolicyResponse(
    val id: UUID,
    val organizationId: UUID,
    val name: String,
    val description: String?,
    val version: Int,
    val isActive: Boolean,
    val definition: PolicyDefinition,
)
