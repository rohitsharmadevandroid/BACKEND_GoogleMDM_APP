package com.primeos.mdm.policy

import com.primeos.mdm.policy.translator.CustomDpcPolicyPayload

data class PolicySyncResult(
    val customDpcPayload: CustomDpcPolicyPayload,
    // Non-null only when the policy's organization has a GMS enterprise
    // and the translated policy was actually pushed there via
    // policies.patch. Null just means "nothing to push to Google" - the
    // custom DPC payload is still produced either way.
    val gmsPolicyName: String?,
)
