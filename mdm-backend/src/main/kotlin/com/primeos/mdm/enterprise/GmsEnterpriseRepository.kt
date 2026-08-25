package com.primeos.mdm.enterprise

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface GmsEnterpriseRepository : JpaRepository<GmsEnterprise, UUID> {
    fun findByOrganizationId(organizationId: UUID): GmsEnterprise?
    fun findByEnterpriseName(enterpriseName: String): GmsEnterprise?
}
