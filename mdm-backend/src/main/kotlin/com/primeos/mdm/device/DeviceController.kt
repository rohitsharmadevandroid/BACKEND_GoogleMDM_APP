package com.primeos.mdm.device

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
class DeviceController(
    private val deviceService: DeviceService,
    private val deviceMigrationService: DeviceMigrationService,
) {

    @GetMapping("/api/organizations/{organizationId}/devices")
    fun listByOrganization(@PathVariable organizationId: UUID): List<DeviceSummary> =
        deviceService.listByOrganization(organizationId)

    @GetMapping("/api/organizations/{organizationId}/gms-migration-tokens")
    fun listMigrationTokens(@PathVariable organizationId: UUID): List<GmsMigrationTokenSummary> =
        deviceMigrationService.listByOrganization(organizationId)

    @GetMapping("/api/devices/{deviceId}")
    fun get(@PathVariable deviceId: UUID): DeviceSummary = deviceService.getSummary(deviceId)

    // policyId present -> assign/change policy; policyId null -> remove
    // the device's policy assignment entirely.
    @PutMapping("/api/devices/{deviceId}/policy")
    fun assignPolicy(
        @PathVariable deviceId: UUID,
        @RequestBody request: AssignDevicePolicyRequest,
    ): DeviceSummary = deviceService.assignPolicy(deviceId, request.policyId)

    @PutMapping("/api/devices/{deviceId}/display-name")
    fun updateDisplayName(
        @PathVariable deviceId: UUID,
        @RequestBody request: UpdateDeviceDisplayNameRequest,
    ): DeviceSummary = deviceService.updateDisplayName(deviceId, request.displayName)

    // Mints a Google DPC-migration token for a device currently managed via
    // our custom (non-GMS) DPC - see DeviceMigrationService for why this is
    // a distinct, separate thing from a fresh GMS enrollment token, and why
    // playDeviceId/playUserId must be supplied by the caller.
    @PostMapping("/api/devices/{deviceId}/gms-migration-token")
    fun createGmsMigrationToken(
        @PathVariable deviceId: UUID,
        @RequestBody request: GmsMigrationTokenRequest,
    ): GmsMigrationTokenResponse = deviceMigrationService.createMigrationToken(deviceId, request)
}
