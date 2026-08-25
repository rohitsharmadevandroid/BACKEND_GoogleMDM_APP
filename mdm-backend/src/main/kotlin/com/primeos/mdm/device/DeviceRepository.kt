package com.primeos.mdm.device

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface DeviceRepository : JpaRepository<Device, UUID> {
    fun findByOrganizationId(organizationId: UUID): List<Device>
    fun findByGmsDeviceResourceName(gmsDeviceResourceName: String): Device?
    fun findByDeviceUid(deviceUid: String): Device?
    fun findByCredentialHash(credentialHash: String): Device?
    fun findByPolicyId(policyId: UUID): List<Device>
}
