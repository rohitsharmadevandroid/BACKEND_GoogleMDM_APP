package com.primeos.mdm.enterprise

import java.util.UUID

class GmsEnterpriseAlreadyExistsException(organizationId: UUID) :
    RuntimeException("Organization $organizationId already has a GMS enterprise")
