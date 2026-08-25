package com.primeos.mdm.policy

import java.util.UUID

class PolicyNotFoundException(policyId: UUID) : RuntimeException("No policy with id $policyId")
