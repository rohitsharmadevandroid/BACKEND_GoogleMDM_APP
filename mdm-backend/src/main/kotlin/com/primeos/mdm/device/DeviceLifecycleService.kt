package com.primeos.mdm.device

import com.primeos.mdm.admin.AdminAccessGuard
import com.primeos.mdm.enterprise.AndroidManagementService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class DeviceLifecycleService(
    private val deviceRepository: DeviceRepository,
    private val androidManagementService: AndroidManagementService,
    private val adminAccessGuard: AdminAccessGuard,
) {

    // GMS: devices.delete wipes AND unenrolls atomically in one call - no
    // ordering problem. NON_GMS: there's no equivalent external call (the
    // device only ever hears from us when it polls /api/dpc/checkin), so
    // this just revokes its credential and marks it DELETED; it does NOT
    // attempt to deliver a final wipe first. If you need the device wiped
    // too, issue and confirm a WIPE command (see DeviceCommandService)
    // BEFORE calling this - revoking the credential here would otherwise
    // stop the device from ever being able to check in and receive one.
    @Transactional
    fun unenroll(deviceId: UUID, request: UnenrollDeviceRequest): DeviceSummary {
        val device = deviceRepository.findById(deviceId).orElseThrow { DeviceNotFoundException(deviceId) }
        adminAccessGuard.requireOrganizationAccess(device.organization.id!!)

        when (device.deviceType) {
            DeviceType.GMS -> {
                val gmsDeviceResourceName = device.gmsDeviceResourceName
                    ?: throw InvalidDeviceStateException("Device $deviceId has no gmsDeviceResourceName yet - it hasn't finished enrolling")
                androidManagementService.deleteDevice(gmsDeviceResourceName, request.wipeDataFlags, request.wipeReasonMessage)
            }
            DeviceType.NON_GMS -> {
                device.credentialRevokedAt = Instant.now()
            }
        }

        device.status = DeviceStatus.DELETED
        val saved = deviceRepository.save(device)

        return DeviceSummary(
            id = saved.id!!,
            deviceType = saved.deviceType,
            displayName = saved.displayName,
            status = saved.status,
            model = saved.model,
            manufacturer = saved.manufacturer,
            osVersion = saved.osVersion,
            lastSeenAt = saved.lastSeenAt,
            policyId = saved.policy?.id,
            policyName = saved.policy?.name,
            gmsDeviceResourceName = saved.gmsDeviceResourceName,
            deviceUid = saved.deviceUid,
        )
    }
}
