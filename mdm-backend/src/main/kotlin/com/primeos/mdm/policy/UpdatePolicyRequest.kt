package com.primeos.mdm.policy

data class UpdatePolicyRequest(
    val definition: PolicyDefinition,
    val changeNote: String? = null,
)
