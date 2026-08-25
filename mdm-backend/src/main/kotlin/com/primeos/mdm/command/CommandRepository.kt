package com.primeos.mdm.command

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface CommandRepository : JpaRepository<Command, UUID> {
    fun findByDeviceIdOrderByCreatedAtDesc(deviceId: UUID): List<Command>
    fun findByOrganizationIdAndStatus(organizationId: UUID, status: CommandStatus): List<Command>
    fun findByDeviceIdAndStatus(deviceId: UUID, status: CommandStatus): List<Command>
}
