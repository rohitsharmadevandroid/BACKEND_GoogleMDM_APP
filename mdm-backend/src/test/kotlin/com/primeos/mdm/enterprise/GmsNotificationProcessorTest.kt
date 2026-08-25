package com.primeos.mdm.enterprise

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.primeos.mdm.command.Command
import com.primeos.mdm.command.CommandRepository
import com.primeos.mdm.command.CommandStatus
import com.primeos.mdm.command.CommandType
import com.primeos.mdm.device.Device
import com.primeos.mdm.device.DeviceRepository
import com.primeos.mdm.device.DeviceStatus
import com.primeos.mdm.device.DeviceType
import com.primeos.mdm.organization.Organization
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import java.time.Instant
import java.util.UUID

class GmsNotificationProcessorTest {

    private val gmsNotificationEventRepository = mock(GmsNotificationEventRepository::class.java)
    private val deviceRepository = mock(DeviceRepository::class.java)
    private val commandRepository = mock(CommandRepository::class.java)

    private val processor = GmsNotificationProcessor(
        gmsNotificationEventRepository,
        deviceRepository,
        commandRepository,
        jacksonObjectMapper(),
    )

    private val organization = Organization(name = "Acme", slug = "acme")

    @Test
    fun `records the raw payload and marks it processed for a recognized type`() {
        given(deviceRepository.findByGmsDeviceResourceName(any())).willReturn(null)
        given(gmsNotificationEventRepository.save(any())).willAnswer { it.arguments[0] }

        processor.process("""{"notificationType":"TEST"}""")

        val captured = argumentCaptorSave()
        assertEquals("TEST", captured.notificationType)
        assertNotNull(captured.processedAt)
        assertNull(captured.processingError)
    }

    @Test
    fun `records a processing error for malformed json instead of throwing`() {
        given(gmsNotificationEventRepository.save(any())).willAnswer { it.arguments[0] }

        processor.process("not valid json")

        val captured = argumentCaptorSave()
        assertNotNull(captured.processingError)
        assertNull(captured.processedAt)
    }

    @Test
    fun `updates device lastSeenAt and activates a provisioning device on a STATUS_REPORT`() {
        val device = Device(
            organization = organization,
            deviceType = DeviceType.GMS,
            gmsDeviceResourceName = "enterprises/LC00abc/devices/1",
            status = DeviceStatus.PROVISIONING,
        )
        given(deviceRepository.findByGmsDeviceResourceName("enterprises/LC00abc/devices/1")).willReturn(device)
        given(gmsNotificationEventRepository.save(any())).willAnswer { it.arguments[0] }

        processor.process("""{"notificationType":"STATUS_REPORT","device":"enterprises/LC00abc/devices/1"}""")

        assertEquals(DeviceStatus.ACTIVE, device.status)
        assertNotNull(device.lastSeenAt)
        verify(deviceRepository).save(device)
    }

    @Test
    fun `marks the most recent in-flight command COMPLETED on a COMMAND notification with no error`() {
        val device = Device(
            organization = organization,
            deviceType = DeviceType.GMS,
            gmsDeviceResourceName = "enterprises/LC00abc/devices/1",
        ).apply { id = UUID.randomUUID() }
        val command = Command(
            organization = organization,
            device = device,
            commandType = CommandType.LOCK,
            status = CommandStatus.SENT,
        ).apply { createdAt = Instant.now() }
        given(deviceRepository.findByGmsDeviceResourceName("enterprises/LC00abc/devices/1")).willReturn(device)
        given(commandRepository.findByDeviceIdAndStatus(device.id!!, CommandStatus.SENT)).willReturn(listOf(command))
        given(gmsNotificationEventRepository.save(any())).willAnswer { it.arguments[0] }

        processor.process("""{"notificationType":"COMMAND","device":"enterprises/LC00abc/devices/1","command":{"type":"LOCK"}}""")

        assertEquals(CommandStatus.COMPLETED, command.status)
        assertNotNull(command.completedAt)
    }

    @Test
    fun `marks the command FAILED when the notification carries an errorCode`() {
        val device = Device(
            organization = organization,
            deviceType = DeviceType.GMS,
            gmsDeviceResourceName = "enterprises/LC00abc/devices/1",
        ).apply { id = UUID.randomUUID() }
        val command = Command(
            organization = organization,
            device = device,
            commandType = CommandType.WIPE,
            status = CommandStatus.SENT,
        ).apply { createdAt = Instant.now() }
        given(deviceRepository.findByGmsDeviceResourceName("enterprises/LC00abc/devices/1")).willReturn(device)
        given(commandRepository.findByDeviceIdAndStatus(device.id!!, CommandStatus.SENT)).willReturn(listOf(command))
        given(gmsNotificationEventRepository.save(any())).willAnswer { it.arguments[0] }

        processor.process(
            """{"notificationType":"COMMAND","device":"enterprises/LC00abc/devices/1","command":{"type":"WIPE","errorCode":"UNKNOWN"}}"""
        )

        assertEquals(CommandStatus.FAILED, command.status)
        assertEquals("UNKNOWN", command.errorMessage)
    }

    @Test
    fun `ignores a device-state notification for a device we don't know about`() {
        given(deviceRepository.findByGmsDeviceResourceName(any())).willReturn(null)
        given(gmsNotificationEventRepository.save(any())).willAnswer { it.arguments[0] }

        processor.process("""{"notificationType":"STATUS_REPORT","device":"enterprises/LC00abc/devices/unknown"}""")

        verify(deviceRepository, never()).save(any())
    }

    private fun argumentCaptorSave(): GmsNotificationEvent {
        val captor = org.mockito.kotlin.argumentCaptor<GmsNotificationEvent>()
        verify(gmsNotificationEventRepository).save(captor.capture())
        return captor.firstValue
    }
}
