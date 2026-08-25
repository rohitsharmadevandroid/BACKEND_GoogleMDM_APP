package com.primeos.mdm.command

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.google.api.services.androidmanagement.v1.model.Operation as GoogleOperation
import com.primeos.mdm.admin.AdminAccessGuard
import com.primeos.mdm.device.Device
import com.primeos.mdm.device.DeviceNotFoundException
import com.primeos.mdm.device.DeviceRepository
import com.primeos.mdm.device.DeviceType
import com.primeos.mdm.device.InvalidDeviceStateException
import com.primeos.mdm.enterprise.AndroidManagementService
import com.primeos.mdm.organization.Organization
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import java.util.Optional
import java.util.UUID

class DeviceCommandServiceTest {

    private val deviceRepository = mock(DeviceRepository::class.java)
    private val commandRepository = mock(CommandRepository::class.java)
    private val androidManagementService = mock(AndroidManagementService::class.java)

    private val service = DeviceCommandService(
        deviceRepository,
        commandRepository,
        GmsCommandTranslator(),
        androidManagementService,
        jacksonObjectMapper(),
        mock(AdminAccessGuard::class.java),
    )

    private val organization = Organization(name = "Acme", slug = "acme").apply { id = UUID.randomUUID() }
    private val deviceId = UUID.randomUUID()

    @Test
    fun `issues a LOCK command against a GMS device and persists it`() {
        val device = Device(
            organization = organization,
            deviceType = DeviceType.GMS,
            gmsDeviceResourceName = "enterprises/LC00abc123/devices/456",
        ).apply { id = deviceId }
        given(deviceRepository.findById(deviceId)).willReturn(Optional.of(device))
        given(androidManagementService.issueCommand(eq("enterprises/LC00abc123/devices/456"), any()))
            .willReturn(GoogleOperation().setName("enterprises/LC00abc123/devices/456/operations/xyz"))
        given(commandRepository.save(any())).willAnswer { it.arguments[0] }

        val command = service.issueCommand(deviceId, CommandType.LOCK, CommandParams(lockDurationSeconds = 300))

        assertEquals(CommandStatus.SENT, command.status)
        assertEquals("enterprises/LC00abc123/devices/456/operations/xyz", command.gmsCommandResourceName)
        assertEquals(CommandType.LOCK, command.commandType)
    }

    @Test
    fun `queues a command as PENDING for a non-GMS device instead of calling Google`() {
        val device = Device(
            organization = organization,
            deviceType = DeviceType.NON_GMS,
            deviceUid = "dpc-install-abc",
        ).apply { id = deviceId }
        given(deviceRepository.findById(deviceId)).willReturn(Optional.of(device))
        given(commandRepository.save(any())).willAnswer { it.arguments[0] }

        val command = service.issueCommand(deviceId, CommandType.WIPE, CommandParams())

        assertEquals(CommandStatus.PENDING, command.status)
        assertEquals(CommandType.WIPE, command.commandType)
        assertNull(command.gmsCommandResourceName)
        verifyNoInteractions(androidManagementService)
    }

    @Test
    fun `rejects a GMS device that has not finished enrolling`() {
        val device = Device(organization = organization, deviceType = DeviceType.GMS).apply { id = deviceId }
        given(deviceRepository.findById(deviceId)).willReturn(Optional.of(device))

        assertThrows(InvalidDeviceStateException::class.java) {
            service.issueCommand(deviceId, CommandType.LOCK, CommandParams())
        }
    }

    @Test
    fun `rejects an unknown device`() {
        given(deviceRepository.findById(deviceId)).willReturn(Optional.empty())

        assertThrows(DeviceNotFoundException::class.java) {
            service.issueCommand(deviceId, CommandType.LOCK, CommandParams())
        }
    }

    @Test
    fun `listCommands decodes the stored payload back into CommandParams`() {
        val device = Device(organization = organization, deviceType = DeviceType.NON_GMS, deviceUid = "dpc-1")
            .apply { id = deviceId }
        val command = Command(
            organization = organization,
            device = device,
            commandType = CommandType.LOCK,
            status = CommandStatus.SENT,
            payload = jacksonObjectMapper().writeValueAsString(CommandParams(lockDurationSeconds = 300)),
        ).apply { id = UUID.randomUUID() }
        given(deviceRepository.findById(deviceId)).willReturn(Optional.of(device))
        given(commandRepository.findByDeviceIdOrderByCreatedAtDesc(deviceId)).willReturn(listOf(command))

        val summaries = service.listCommands(deviceId)

        assertEquals(1, summaries.size)
        assertEquals(300L, summaries.single().payload.lockDurationSeconds)
    }
}
