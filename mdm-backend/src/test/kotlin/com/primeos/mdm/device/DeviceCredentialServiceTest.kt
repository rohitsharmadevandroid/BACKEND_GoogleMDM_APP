package com.primeos.mdm.device

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

class DeviceCredentialServiceTest {

    private val service = DeviceCredentialService()

    @Test
    fun `generate produces a raw key whose hash matches hash()`() {
        val credential = service.generate()

        assertEquals(credential.hash, service.hash(credential.rawKey))
    }

    @Test
    fun `two generated credentials are never the same`() {
        val first = service.generate()
        val second = service.generate()

        assertNotEquals(first.rawKey, second.rawKey)
        assertNotEquals(first.hash, second.hash)
    }

    @Test
    fun `hash is deterministic for the same input`() {
        assertEquals(service.hash("same-key"), service.hash("same-key"))
    }
}
