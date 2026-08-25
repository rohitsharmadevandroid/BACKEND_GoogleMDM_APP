package com.primeos.mdm.dpc

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.primeos.mdm.command.Command
import com.primeos.mdm.command.CommandParams
import com.primeos.mdm.command.CommandRepository
import com.primeos.mdm.command.CommandStatus
import com.primeos.mdm.command.CommandType
import com.primeos.mdm.device.Device
import com.primeos.mdm.device.DeviceRepository
import com.primeos.mdm.device.DeviceStatus
import com.primeos.mdm.device.DeviceType
import com.primeos.mdm.organization.Organization
import com.primeos.mdm.policy.Policy
import com.primeos.mdm.policy.PolicyDefinitionCodec
import com.primeos.mdm.policy.translator.CustomDpcPolicyTranslator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import java.util.UUID

class DpcCheckInServiceTest {

    private val deviceRepository = mock(DeviceRepository::class.java)
    private val commandRepository = mock(CommandRepository::class.java)

    private val service = DpcCheckInService(
        deviceRepository,
        commandRepository,
        PolicyDefinitionCodec(jacksonObjectMapper()),
        CustomDpcPolicyTranslator(),
        jacksonObjectMapper(),
        60L,
    )

    private val organization = Organization(name = "Acme", slug = "acme")

    @Test
    fun `check-in with no policy and no pending commands marks the device active`() {
        val device = Device(
            organization = organization,
            deviceType = DeviceType.NON_GMS,
            deviceUid = "dpc-1",
            status = DeviceStatus.PROVISIONING,
        ).apply { id = UUID.randomUUID() }
        given(commandRepository.findByDeviceIdAndStatus(device.id!!, CommandStatus.PENDING)).willReturn(emptyList())

        val response = service.checkIn(device, DpcCheckInRequest())

        assertEquals(DeviceStatus.ACTIVE, device.status)
        assertNotNull(device.lastSeenAt)
        assertNull(response.policy)
        assertTrue(response.pendingCommands.isEmpty())
        assertEquals(60L, response.checkInIntervalSeconds)
    }

    @Test
    fun `sends the policy payload when the device's applied version is stale`() {
        val policy = Policy(
            organization = organization,
            name = "Default",
            version = 3,
            definition = """{"cameraDisabled": true}""",
        )
        val device = Device(
            organization = organization,
            deviceType = DeviceType.NON_GMS,
            deviceUid = "dpc-1",
            policy = policy,
        ).apply { id = UUID.randomUUID() }
        given(commandRepository.findByDeviceIdAndStatus(device.id!!, CommandStatus.PENDING)).willReturn(emptyList())

        val response = service.checkIn(device, DpcCheckInRequest(lastPolicyVersionApplied = 2))

        assertEquals(3, response.policyVersion)
        assertEquals(true, response.policy?.cameraDisabled)
    }

    @Test
    fun `omits the policy payload when the device already applied the current version`() {
        val policy = Policy(organization = organization, name = "Default", version = 3, definition = "{}")
        val device = Device(
            organization = organization,
            deviceType = DeviceType.NON_GMS,
            deviceUid = "dpc-1",
            policy = policy,
        ).apply { id = UUID.randomUUID() }
        given(commandRepository.findByDeviceIdAndStatus(device.id!!, CommandStatus.PENDING)).willReturn(emptyList())

        val response = service.checkIn(device, DpcCheckInRequest(lastPolicyVersionApplied = 3))

        assertNull(response.policy)
        assertEquals(3, response.policyVersion)
    }

    @Test
    fun `returns pending commands and marks them SENT`() {
        val device = Device(organization = organization, deviceType = DeviceType.NON_GMS, deviceUid = "dpc-1")
            .apply { id = UUID.randomUUID() }
        val command = Command(
            organization = organization,
            device = device,
            commandType = CommandType.LOCK,
            status = CommandStatus.PENDING,
            payload = jacksonObjectMapper().writeValueAsString(CommandParams(lockDurationSeconds = 300)),
        ).apply { id = UUID.randomUUID() }
        given(commandRepository.findByDeviceIdAndStatus(device.id!!, CommandStatus.PENDING)).willReturn(listOf(command))
        given(commandRepository.saveAll(any<List<Command>>())).willAnswer { it.arguments[0] }

        val response = service.checkIn(device, DpcCheckInRequest())

        assertEquals(1, response.pendingCommands.size)
        assertEquals(CommandType.LOCK, response.pendingCommands.single().type)
        assertEquals(300L, response.pendingCommands.single().params.lockDurationSeconds)
        assertEquals(CommandStatus.SENT, command.status)
    }
}
