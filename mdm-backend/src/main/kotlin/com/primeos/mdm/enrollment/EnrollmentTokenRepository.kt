package com.primeos.mdm.enrollment

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface EnrollmentTokenRepository : JpaRepository<EnrollmentToken, UUID> {
    fun findByTokenValue(tokenValue: String): EnrollmentToken?
    fun findByOrganizationId(organizationId: UUID): List<EnrollmentToken>
}
