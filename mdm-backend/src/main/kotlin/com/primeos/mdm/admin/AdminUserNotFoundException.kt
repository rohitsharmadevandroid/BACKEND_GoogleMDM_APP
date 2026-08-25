package com.primeos.mdm.admin

import java.util.UUID

class AdminUserNotFoundException(adminUserId: UUID) : RuntimeException("No admin user with id $adminUserId")
