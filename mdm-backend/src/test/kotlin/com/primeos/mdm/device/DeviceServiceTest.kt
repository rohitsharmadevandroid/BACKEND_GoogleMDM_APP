package com.primeos.mdm.device

import com.primeos.mdm.admin.AdminAccessGuard
import com.primeos.mdm.organization.Organization
import com.primeos.mdm.policy.Policy
import com.primeos.mdm.policy.PolicyNotFoundException
import com.primeos.mdm.policy.PolicyRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import java.util.Optional
import java.util.UUID

class DeviceServiceTest {

    private val deviceRepository = mock(DeviceRepository::class.java)
    private val policyRepository = mock(PolicyRepository::class.java)
    private val deviceService = DeviceService(deviceRepository, policyRepository, mock(AdminAccessGuard::class.java))

    private val organization = Organization(name = "Acme", slug = "acme").apply { id = UUID.randomUUID() }

    @Test
    fun `saves a GMS device that has a resource name`() {
        val device = Device(
            organization = organization,
            deviceType = DeviceType.GMS,
            gmsDeviceResourceName = "enterprises/LC00abc123/devices/456",
        )
        given(deviceRepository.save(device)).willReturn(device)

        assertEquals(device, deviceService.save(device))
    }

    @Test
    fun `rejects a GMS device with no resource name`() {
        val device = Device(organization = organization, deviceType = DeviceType.GMS)

        assertThrows(InvalidDeviceStateException::class.java) {
            deviceService.save(device)
        }
    }

    @Test
    fun `saves a NON_GMS device that has a device uid`() {
        val device = Device(
            organization = organization,
            deviceType = DeviceType.NON_GMS,
            deviceUid = "dpc-install-abc123",
        )
        given(deviceRepository.save(device)).willReturn(device)

        assertEquals(device, deviceService.save(device))
    }

    @Test
    fun `rejects a NON_GMS device with no device uid`() {
        val device = Device(organization = organization, deviceType = DeviceType.NON_GMS)

        assertThrows(InvalidDeviceStateException::class.java) {
            deviceService.save(device)
        }
    }

    @Test
    fun `summary includes the assigned policy's id and name`() {
        val policy = Policy(organization = organization, name = "Kiosk", definition = "{}")
            .apply { id = UUID.randomUUID() }
        val deviceId = UUID.randomUUID()
        val device = Device(
            organization = organization,
            deviceType = DeviceType.NON_GMS,
            deviceUid = "dpc-1",
            policy = policy,
        ).apply { id = deviceId }
        given(deviceRepository.findById(deviceId)).willReturn(Optional.of(device))

        val summary = deviceService.getSummary(deviceId)

        assertEquals(policy.id, summary.policyId)
        assertEquals("Kiosk", summary.policyName)
        assertEquals(DeviceType.NON_GMS, summary.deviceType)
    }

    @Test
    fun `listByOrganization maps every device in the org to a summary`() {
        val organizationId = UUID.randomUUID()
        val gmsDevice = Device(
            organization = organization,
            deviceType = DeviceType.GMS,
            gmsDeviceResourceName = "enterprises/LC00abc/devices/1",
        ).apply { id = UUID.randomUUID() }
        val nonGmsDevice = Device(organization = organization, deviceType = DeviceType.NON_GMS, deviceUid = "dpc-1")
            .apply { id = UUID.randomUUID() }
        given(deviceRepository.findByOrganizationId(organizationId)).willReturn(listOf(gmsDevice, nonGmsDevice))

        val summaries = deviceService.listByOrganization(organizationId)

        assertEquals(2, summaries.size)
        assertEquals(setOf(DeviceType.GMS, DeviceType.NON_GMS), summaries.map { it.deviceType }.toSet())
    }

    @Test
    fun `assignPolicy attaches a policy from the same organization`() {
        val deviceId = UUID.randomUUID()
        val device = Device(organization = organization, deviceType = DeviceType.NON_GMS, deviceUid = "dpc-1")
            .apply { id = deviceId }
        val policyId = UUID.randomUUID()
        val policy = Policy(organization = organization, name = "Kiosk", definition = "{}").apply { id = policyId }
        given(deviceRepository.findById(deviceId)).willReturn(Optional.of(device))
        given(policyRepository.findById(policyId)).willReturn(Optional.of(policy))
        given(deviceRepository.save(any())).willAnswer { it.arguments[0] }

        val summary = deviceService.assignPolicy(deviceId, policyId)

        assertEquals(policyId, summary.policyId)
        assertEquals("Kiosk", summary.policyName)
    }

    @Test
    fun `assignPolicy with a null policyId removes the device's policy`() {
        val deviceId = UUID.randomUUID()
        val existingPolicy = Policy(organization = organization, name = "Kiosk", definition = "{}")
            .apply { id = UUID.randomUUID() }
        val device = Device(
            organization = organization,
            deviceType = DeviceType.NON_GMS,
            deviceUid = "dpc-1",
            policy = existingPolicy,
        ).apply { id = deviceId }
        given(deviceRepository.findById(deviceId)).willReturn(Optional.of(device))
        given(deviceRepository.save(any())).willAnswer { it.arguments[0] }

        val summary = deviceService.assignPolicy(deviceId, null)

        assertNull(summary.policyId)
        assertNull(summary.policyName)
    }

    @Test
    fun `assignPolicy rejects an unknown device`() {
        val deviceId = UUID.randomUUID()
        given(deviceRepository.findById(deviceId)).willReturn(Optional.empty())

        assertThrows(DeviceNotFoundException::class.java) {
            deviceService.assignPolicy(deviceId, UUID.randomUUID())
        }
    }

    @Test
    fun `assignPolicy rejects an unknown policy`() {
        val deviceId = UUID.randomUUID()
        val device = Device(organization = organization, deviceType = DeviceType.NON_GMS, deviceUid = "dpc-1")
            .apply { id = deviceId }
        val policyId = UUID.randomUUID()
        given(deviceRepository.findById(deviceId)).willReturn(Optional.of(device))
        given(policyRepository.findById(policyId)).willReturn(Optional.empty())

        assertThrows(PolicyNotFoundException::class.java) {
            deviceService.assignPolicy(deviceId, policyId)
        }
    }

    @Test
    fun `assignPolicy rejects a policy from a different organization`() {
        val deviceId = UUID.randomUUID()
        val device = Device(organization = organization, deviceType = DeviceType.NON_GMS, deviceUid = "dpc-1")
            .apply { id = deviceId }
        val otherOrganization = Organization(name = "Other", slug = "other").apply { id = UUID.randomUUID() }
        val policyId = UUID.randomUUID()
        val policy = Policy(organization = otherOrganization, name = "Other Policy", definition = "{}")
            .apply { id = policyId }
        given(deviceRepository.findById(deviceId)).willReturn(Optional.of(device))
        given(policyRepository.findById(policyId)).willReturn(Optional.of(policy))

        assertThrows(PolicyOrganizationMismatchException::class.java) {
            deviceService.assignPolicy(deviceId, policyId)
        }
    }

    @Test
    fun `updateDisplayName sets a trimmed display name`() {
        val deviceId = UUID.randomUUID()
        val device = Device(organization = organization, deviceType = DeviceType.NON_GMS, deviceUid = "dpc-1")
            .apply { id = deviceId }
        given(deviceRepository.findById(deviceId)).willReturn(Optional.of(device))
        given(deviceRepository.save(any())).willAnswer { it.arguments[0] }

        val summary = deviceService.updateDisplayName(deviceId, "  Primebook - Front Desk  ")

        assertEquals("Primebook - Front Desk", summary.displayName)
    }

    @Test
    fun `updateDisplayName with a blank string clears the display name`() {
        val deviceId = UUID.randomUUID()
        val device = Device(
            organization = organization,
            deviceType = DeviceType.NON_GMS,
            deviceUid = "dpc-1",
            displayName = "Old Name",
        ).apply { id = deviceId }
        given(deviceRepository.findById(deviceId)).willReturn(Optional.of(device))
        given(deviceRepository.save(any())).willAnswer { it.arguments[0] }

        val summary = deviceService.updateDisplayName(deviceId, "   ")

        assertNull(summary.displayName)
    }

    @Test
    fun `updateDisplayName rejects an unknown device`() {
        val deviceId = UUID.randomUUID()
        given(deviceRepository.findById(deviceId)).willReturn(Optional.empty())

        assertThrows(DeviceNotFoundException::class.java) {
            deviceService.updateDisplayName(deviceId, "New Name")
        }
    }
}
