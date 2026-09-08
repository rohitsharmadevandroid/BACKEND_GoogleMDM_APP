package com.primeos.mdm.device

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface GmsMigrationTokenRepository : JpaRepository<GmsMigrationToken, UUID> {
    fun findByOrganizationIdOrderByCreatedAtDesc(organizationId: UUID): List<GmsMigrationToken>
}
