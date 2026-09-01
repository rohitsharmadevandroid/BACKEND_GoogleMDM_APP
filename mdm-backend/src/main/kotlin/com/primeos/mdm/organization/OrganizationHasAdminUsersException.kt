package com.primeos.mdm.organization

import java.util.UUID

class OrganizationHasAdminUsersException(organizationId: UUID, count: Int) :
    RuntimeException("Organization $organizationId still has $count admin user(s) - deactivate or reassign them before deleting the organization")
