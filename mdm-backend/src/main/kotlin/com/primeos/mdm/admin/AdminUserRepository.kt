package com.primeos.mdm.admin

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface AdminUserRepository : JpaRepository<AdminUser, UUID> {
    fun findByEmail(email: String): AdminUser?
    fun findByOrganizationId(organizationId: UUID): List<AdminUser>
}
