package com.primeos.mdm.policy

data class CreatePolicyRequest(
    val name: String,
    val description: String? = null,
    val definition: PolicyDefinition = PolicyDefinition(),
)
