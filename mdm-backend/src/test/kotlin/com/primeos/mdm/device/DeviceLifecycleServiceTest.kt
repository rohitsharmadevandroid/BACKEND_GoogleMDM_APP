package com.primeos.mdm.device

import com.primeos.mdm.admin.AdminAccessGuard
import com.primeos.mdm.enterprise.AndroidManagementService
import com.primeos.mdm.organization.Organization
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import java.util.Optional
import java.util.UUID

class DeviceLifecycleServiceTest {

    private val deviceRepository = mock(DeviceRepository::class.java)
    private val androidManagementService = mock(AndroidManagementService::class.java)
    private val service = DeviceLifecycleService(deviceRepository, androidManagementService, mock(AdminAccessGuard::class.java))

    private val organization = Organization(name = "Acme", slug = "acme").apply { id = UUID.randomUUID() }
    private val deviceId = UUID.randomUUID()

    @Test
    fun `unenrolling a GMS device calls deviceLifecycle delete and marks it DELETED`() {
        val device = Device(
            organization = organization,
            deviceType = DeviceType.GMS,
            gmsDeviceResourceName = "enterprises/LC00abc/devices/1",
        ).apply { id = deviceId }
        given(deviceRepository.findById(deviceId)).willReturn(Optional.of(device))
        given(deviceRepository.save(any())).willAnswer { it.arguments[0] }

        val result = service.unenroll(
            deviceId,
            UnenrollDeviceRequest(wipeDataFlags = listOf("WIPE_EXTERNAL_STORAGE"), wipeReasonMessage = "retired"),
        )

        assertEquals(DeviceStatus.DELETED, result.status)
        verify(androidManagementService).deleteDevice(
            eq("enterprises/LC00abc/devices/1"),
            eq(listOf("WIPE_EXTERNAL_STORAGE")),
            eq("retired"),
        )
    }

    @Test
    fun `unenrolling a non-GMS device revokes its credential instead of calling Google`() {
        val device = Device(organization = organization, deviceType = DeviceType.NON_GMS, deviceUid = "dpc-1")
            .apply { id = deviceId }
        given(deviceRepository.findById(deviceId)).willReturn(Optional.of(device))
        given(deviceRepository.save(any())).willAnswer { it.arguments[0] }

        val result = service.unenroll(deviceId, UnenrollDeviceRequest())

        assertEquals(DeviceStatus.DELETED, result.status)
        assertNotNull(device.credentialRevokedAt)
        verify(androidManagementService, never()).deleteDevice(any(), any(), any())
    }

    @Test
    fun `rejects a GMS device that has not finished enrolling`() {
        val device = Device(organization = organization, deviceType = DeviceType.GMS).apply { id = deviceId }
        given(deviceRepository.findById(deviceId)).willReturn(Optional.of(device))

        assertThrows(InvalidDeviceStateException::class.java) {
            service.unenroll(deviceId, UnenrollDeviceRequest())
        }
    }

    @Test
    fun `rejects an unknown device`() {
        given(deviceRepository.findById(deviceId)).willReturn(Optional.empty())

        assertThrows(DeviceNotFoundException::class.java) {
            service.unenroll(deviceId, UnenrollDeviceRequest())
        }
    }
}
