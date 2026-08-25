package com.primeos.mdm.enterprise

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface GmsEnterpriseSignupRepository : JpaRepository<GmsEnterpriseSignup, UUID> {
    fun findFirstByOrganizationIdAndConsumedAtIsNullOrderByCreatedAtDesc(organizationId: UUID): GmsEnterpriseSignup?
}
