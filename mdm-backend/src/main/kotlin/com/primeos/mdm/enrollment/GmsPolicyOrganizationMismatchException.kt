package com.primeos.mdm.enrollment

import java.util.UUID

class GmsPolicyOrganizationMismatchException(policyId: UUID, organizationId: UUID) :
    RuntimeException("Policy $policyId does not belong to organization $organizationId")
