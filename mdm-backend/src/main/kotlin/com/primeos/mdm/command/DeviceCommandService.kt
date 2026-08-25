package com.primeos.mdm.command

import com.fasterxml.jackson.databind.ObjectMapper
import com.primeos.mdm.admin.AdminAccessGuard
import com.primeos.mdm.device.Device
import com.primeos.mdm.device.DeviceNotFoundException
import com.primeos.mdm.device.DeviceRepository
import com.primeos.mdm.device.DeviceType
import com.primeos.mdm.device.InvalidDeviceStateException
import com.primeos.mdm.enterprise.AndroidManagementService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class DeviceCommandService(
    private val deviceRepository: DeviceRepository,
    private val commandRepository: CommandRepository,
    private val gmsCommandTranslator: GmsCommandTranslator,
    private val androidManagementService: AndroidManagementService,
    private val objectMapper: ObjectMapper,
    private val adminAccessGuard: AdminAccessGuard,
) {

    // One admin-facing entry point for both paths - this is the "backend
    // dispatches differently depending on device type" half of the unified
    // command model. GMS dispatches immediately via issueCommand; non-GMS
    // has no direct channel to the device, so it just queues PENDING and
    // gets delivered on the device's next /api/dpc/checkin.
    @Transactional
    fun issueCommand(deviceId: UUID, commandType: CommandType, params: CommandParams = CommandParams()): Command {
        val device = deviceRepository.findById(deviceId).orElseThrow { DeviceNotFoundException(deviceId) }
        adminAccessGuard.requireOrganizationAccess(device.organization.id!!)

        return when (device.deviceType) {
            DeviceType.GMS -> issueGmsCommand(device, commandType, params)
            DeviceType.NON_GMS -> queueNonGmsCommand(device, commandType, params)
        }
    }

    private fun issueGmsCommand(device: Device, commandType: CommandType, params: CommandParams): Command {
        val gmsDeviceResourceName = device.gmsDeviceResourceName
            ?: throw InvalidDeviceStateException("Device ${device.id} has no gmsDeviceResourceName yet - it hasn't finished enrolling")

        val googleCommand = gmsCommandTranslator.translate(commandType, params)
        val operation = androidManagementService.issueCommand(gmsDeviceResourceName, googleCommand)

        return commandRepository.save(
            Command(
                organization = device.organization,
                device = device,
                commandType = commandType,
                status = CommandStatus.SENT,
                payload = objectMapper.writeValueAsString(params),
                gmsCommandResourceName = operation.name,
                dispatchedAt = Instant.now(),
            )
        )
    }

    private fun queueNonGmsCommand(device: Device, commandType: CommandType, params: CommandParams): Command =
        commandRepository.save(
            Command(
                organization = device.organization,
                device = device,
                commandType = commandType,
                status = CommandStatus.PENDING,
                payload = objectMapper.writeValueAsString(params),
            )
        )

    @Transactional(readOnly = true)
    fun listCommands(deviceId: UUID): List<CommandSummary> {
        val device = deviceRepository.findById(deviceId).orElseThrow { DeviceNotFoundException(deviceId) }
        adminAccessGuard.requireOrganizationAccess(device.organization.id!!)

        return commandRepository.findByDeviceIdOrderByCreatedAtDesc(deviceId).map {
            CommandSummary(
                id = it.id!!,
                commandType = it.commandType,
                status = it.status,
                payload = objectMapper.readValue(it.payload, CommandParams::class.java),
                errorMessage = it.errorMessage,
                dispatchedAt = it.dispatchedAt,
                completedAt = it.completedAt,
                createdAt = it.createdAt,
            )
        }
    }
}
