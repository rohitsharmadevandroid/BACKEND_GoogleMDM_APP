package com.primeos.mdm.admin

import java.util.UUID

class OrganizationAccessDeniedException(organizationId: UUID) :
    RuntimeException("Not authorized to access organization $organizationId")
