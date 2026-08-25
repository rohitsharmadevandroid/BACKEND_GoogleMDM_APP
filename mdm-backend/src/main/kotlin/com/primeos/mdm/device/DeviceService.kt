package com.primeos.mdm.device

import com.primeos.mdm.admin.AdminAccessGuard
import com.primeos.mdm.policy.PolicyNotFoundException
import com.primeos.mdm.policy.PolicyRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class DeviceService(
    private val deviceRepository: DeviceRepository,
    private val policyRepository: PolicyRepository,
    private val adminAccessGuard: AdminAccessGuard,
) {

    // save()/findById() aren't reached from any admin-authenticated
    // controller today (device creation goes through deviceRepository
    // directly from the GMS callback and DPC enroll flows, neither of
    // which has an admin identity to check) - no AdminAccessGuard call
    // here. Add one if either ever gets wired to an admin endpoint.
    @Transactional
    fun save(device: Device): Device {
        validateIdentity(device)
        return deviceRepository.save(device)
    }

    @Transactional(readOnly = true)
    fun findById(id: UUID): Device? = deviceRepository.findById(id).orElse(null)

    @Transactional(readOnly = true)
    fun listByOrganization(organizationId: UUID): List<DeviceSummary> {
        adminAccessGuard.requireOrganizationAccess(organizationId)
        return deviceRepository.findByOrganizationId(organizationId).map { it.toSummary() }
    }

    @Transactional(readOnly = true)
    fun getSummary(deviceId: UUID): DeviceSummary {
        val device = deviceRepository.findById(deviceId).orElseThrow { DeviceNotFoundException(deviceId) }
        adminAccessGuard.requireOrganizationAccess(device.organization.id!!)
        return device.toSummary()
    }

    // Assigns, changes, or removes a device's policy after enrollment - the
    // only other place Device.policy is ever set is DpcEnrollmentService,
    // from the enrollment token's defaultPolicy, which only applies once at
    // enrollment time. This is the admin-facing path for every case after
    // that.
    @Transactional
    fun assignPolicy(deviceId: UUID, policyId: UUID?): DeviceSummary {
        val device = deviceRepository.findById(deviceId).orElseThrow { DeviceNotFoundException(deviceId) }
        adminAccessGuard.requireOrganizationAccess(device.organization.id!!)

        device.policy = policyId?.let { id ->
            val policy = policyRepository.findById(id).orElseThrow { PolicyNotFoundException(id) }
            if (policy.organization.id != device.organization.id) {
                throw PolicyOrganizationMismatchException(id, deviceId)
            }
            policy
        }

        return deviceRepository.save(device).toSummary()
    }

    // Purely an admin-facing label - the DPC check-in protocol never sets
    // this (see DpcCheckInService, which only ever writes osVersion/model/
    // manufacturer from the device's own report). Needed because model/
    // manufacturer alone can't distinguish two devices of the same
    // hardware in the dashboard's device list/policy picker.
    @Transactional
    fun updateDisplayName(deviceId: UUID, displayName: String?): DeviceSummary {
        val device = deviceRepository.findById(deviceId).orElseThrow { DeviceNotFoundException(deviceId) }
        adminAccessGuard.requireOrganizationAccess(device.organization.id!!)

        device.displayName = displayName?.trim()?.ifEmpty { null }
        return deviceRepository.save(device).toSummary()
    }

    private fun Device.toSummary() = DeviceSummary(
        id = id!!,
        deviceType = deviceType,
        displayName = displayName,
        status = status,
        model = model,
        manufacturer = manufacturer,
        osVersion = osVersion,
        lastSeenAt = lastSeenAt,
        policyId = policy?.id,
        policyName = policy?.name,
        gmsDeviceResourceName = gmsDeviceResourceName,
        deviceUid = deviceUid,
    )

    // devices carries a CHECK constraint requiring GMS rows to have
    // gms_device_resource_name and NON_GMS rows to have device_uid (see
    // V7__create_devices.sql). Hibernate has no concept of DB CHECK
    // constraints, so without this the same rule only ever surfaces as a
    // raw PSQLException from the INSERT/UPDATE - this fails earlier, with a
    // message that actually names what's missing, on every save() call
    // (create or update) rather than only at initial creation.
    private fun validateIdentity(device: Device) {
        when (device.deviceType) {
            DeviceType.GMS ->
                if (device.gmsDeviceResourceName.isNullOrBlank()) {
                    throw InvalidDeviceStateException("GMS device requires gmsDeviceResourceName")
                }

            DeviceType.NON_GMS ->
                if (device.deviceUid.isNullOrBlank()) {
                    throw InvalidDeviceStateException("NON_GMS device requires deviceUid")
                }
        }
    }
}
