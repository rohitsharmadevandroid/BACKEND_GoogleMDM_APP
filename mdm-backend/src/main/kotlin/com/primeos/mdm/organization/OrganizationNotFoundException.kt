package com.primeos.mdm.organization

import java.util.UUID

class OrganizationNotFoundException(organizationId: UUID) :
    RuntimeException("No organization with id $organizationId")
