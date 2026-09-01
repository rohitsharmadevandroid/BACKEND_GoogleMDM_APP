package com.primeos.mdm.dpc

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.primeos.mdm.command.Command
import com.primeos.mdm.command.CommandNotFoundException
import com.primeos.mdm.command.CommandRepository
import com.primeos.mdm.command.CommandStatus
import com.primeos.mdm.command.CommandType
import com.primeos.mdm.device.Device
import com.primeos.mdm.device.DeviceType
import com.primeos.mdm.organization.Organization
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import java.util.Optional
import java.util.UUID

class DpcCommandAckServiceTest {

    private val commandRepository = mock(CommandRepository::class.java)
    private val service = DpcCommandAckService(commandRepository, jacksonObjectMapper())

    private val organization = Organization(name = "Acme", slug = "acme")
    private val device = Device(organization = organization, deviceType = DeviceType.NON_GMS, deviceUid = "dpc-1")
        .apply { id = UUID.randomUUID() }

    @Test
    fun `marks a command completed`() {
        val commandId = UUID.randomUUID()
        val command = Command(
            organization = organization,
            device = device,
            commandType = CommandType.LOCK,
            status = CommandStatus.SENT,
        ).apply { id = commandId }
        given(commandRepository.findById(commandId)).willReturn(Optional.of(command))
        given(commandRepository.save(any())).willAnswer { it.arguments[0] }

        val result = service.ack(device, commandId, DpcCommandAckRequest(status = CommandStatus.COMPLETED))

        assertEquals(CommandStatus.COMPLETED, result.status)
        assertNotNull(result.completedAt)
    }

    @Test
    fun `records the error message on failure`() {
        val commandId = UUID.randomUUID()
        val command = Command(organization = organization, device = device, commandType = CommandType.WIPE, status = CommandStatus.SENT)
            .apply { id = commandId }
        given(commandRepository.findById(commandId)).willReturn(Optional.of(command))
        given(commandRepository.save(any())).willAnswer { it.arguments[0] }

        val result = service.ack(device, commandId, DpcCommandAckRequest(status = CommandStatus.FAILED, errorMessage = "denied"))

        assertEquals(CommandStatus.FAILED, result.status)
        assertEquals("denied", result.errorMessage)
    }

    @Test
    fun `stores collected device info as JSON in resultData`() {
        val commandId = UUID.randomUUID()
        val command = Command(
            organization = organization,
            device = device,
            commandType = CommandType.REQUEST_DEVICE_INFO,
            status = CommandStatus.SENT,
        ).apply { id = commandId }
        given(commandRepository.findById(commandId)).willReturn(Optional.of(command))
        given(commandRepository.save(any())).willAnswer { it.arguments[0] }

        val result = service.ack(
            device,
            commandId,
            DpcCommandAckRequest(status = CommandStatus.COMPLETED, resultData = mapOf("imei" to "123456789012345", "batteryLevel" to "84")),
        )

        assertEquals("""{"imei":"123456789012345","batteryLevel":"84"}""", result.resultData)
    }

    @Test
    fun `leaves resultData untouched when the ack doesn't include any`() {
        val commandId = UUID.randomUUID()
        val command = Command(organization = organization, device = device, commandType = CommandType.LOCK, status = CommandStatus.SENT)
            .apply { id = commandId }
        given(commandRepository.findById(commandId)).willReturn(Optional.of(command))
        given(commandRepository.save(any())).willAnswer { it.arguments[0] }

        val result = service.ack(device, commandId, DpcCommandAckRequest(status = CommandStatus.COMPLETED))

        assertEquals("{}", result.resultData)
    }

    @Test
    fun `rejects an unknown command`() {
        val commandId = UUID.randomUUID()
        given(commandRepository.findById(commandId)).willReturn(Optional.empty())

        assertThrows(CommandNotFoundException::class.java) {
            service.ack(device, commandId, DpcCommandAckRequest(status = CommandStatus.COMPLETED))
        }
    }

    @Test
    fun `rejects a command that belongs to a different device`() {
        val commandId = UUID.randomUUID()
        val otherDevice = Device(organization = organization, deviceType = DeviceType.NON_GMS, deviceUid = "dpc-2")
            .apply { id = UUID.randomUUID() }
        val command = Command(organization = organization, device = otherDevice, commandType = CommandType.LOCK, status = CommandStatus.SENT)
            .apply { id = commandId }
        given(commandRepository.findById(commandId)).willReturn(Optional.of(command))

        assertThrows(CommandNotFoundException::class.java) {
            service.ack(device, commandId, DpcCommandAckRequest(status = CommandStatus.COMPLETED))
        }
    }
}
