package com.primeos.mdm.policy

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface PolicyRepository : JpaRepository<Policy, UUID> {
    fun findByOrganizationId(organizationId: UUID): List<Policy>
    fun findByOrganizationIdAndName(organizationId: UUID, name: String): Policy?
    fun findByOrganizationIdAndIsActiveTrue(organizationId: UUID): List<Policy>
}
