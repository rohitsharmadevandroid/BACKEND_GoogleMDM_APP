package com.primeos.mdm.enrollment

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NonGmsProvisioningQrCodeBuilderTest {

    private val objectMapper = jacksonObjectMapper()

    @Test
    fun `is not configured when any of the three values is blank`() {
        assertFalse(NonGmsProvisioningQrCodeBuilder(objectMapper, "", "https://example.com/app.apk", "checksum").isConfigured())
        assertFalse(NonGmsProvisioningQrCodeBuilder(objectMapper, "pkg/Receiver", "", "checksum").isConfigured())
        assertFalse(NonGmsProvisioningQrCodeBuilder(objectMapper, "pkg/Receiver", "https://example.com/app.apk", "").isConfigured())
    }

    @Test
    fun `build returns null when not configured`() {
        val builder = NonGmsProvisioningQrCodeBuilder(objectMapper, "", "", "")
        assertNull(builder.build("some-token"))
    }

    @Test
    fun `build produces the real provisioning extras when fully configured`() {
        val builder = NonGmsProvisioningQrCodeBuilder(
            objectMapper,
            "com.floydwiz.googlemdm/com.floydwiz.googlemdm.MyDeviceAdminReceiver",
            "https://localhost:8080/api/dpc/apk",
            "abc123checksum",
        )
        assertTrue(builder.isConfigured())

        val json = builder.build("tok-value-123")
        assertTrue(json != null)

        val decoded = objectMapper.readValue(json, Map::class.java)
        assertEquals(
            "com.floydwiz.googlemdm/com.floydwiz.googlemdm.MyDeviceAdminReceiver",
            decoded["android.app.extra.PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME"],
        )
        assertEquals("https://localhost:8080/api/dpc/apk", decoded["android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION"])
        assertEquals("abc123checksum", decoded["android.app.extra.PROVISIONING_DEVICE_ADMIN_SIGNATURE_CHECKSUM"])

        @Suppress("UNCHECKED_CAST")
        val extrasBundle = decoded["android.app.extra.PROVISIONING_ADMIN_EXTRAS_BUNDLE"] as Map<String, Any>
        assertEquals("tok-value-123", extrasBundle["enrollmentToken"])
    }
}
