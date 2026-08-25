package com.primeos.mdm.device

import java.util.UUID

class PolicyOrganizationMismatchException(policyId: UUID, deviceId: UUID) :
    RuntimeException("Policy $policyId does not belong to the same organization as device $deviceId")
