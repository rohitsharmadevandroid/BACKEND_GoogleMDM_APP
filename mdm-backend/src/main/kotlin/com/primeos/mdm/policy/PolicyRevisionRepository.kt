package com.primeos.mdm.policy

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface PolicyRevisionRepository : JpaRepository<PolicyRevision, UUID> {
    fun findByPolicyIdOrderByVersionDesc(policyId: UUID): List<PolicyRevision>
}
