package com.primeos.mdm.enterprise

import java.util.UUID

class NoGmsEnterpriseException(organizationId: UUID) :
    RuntimeException("Organization $organizationId has no GMS enterprise yet - complete enterprise sign-up first")
