package com.primeos.mdm.command

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class GmsCommandTranslatorTest {

    private val translator = GmsCommandTranslator()

    @Test
    fun `translates LOCK with a duration`() {
        val command = translator.translate(CommandType.LOCK, CommandParams(lockDurationSeconds = 300))

        assertEquals("LOCK", command.type)
        assertEquals("300s", command.duration)
    }

    @Test
    fun `translates REBOOT with no extra params`() {
        val command = translator.translate(CommandType.REBOOT, CommandParams())

        assertEquals("REBOOT", command.type)
        assertNull(command.duration)
    }

    @Test
    fun `translates RESET_PASSWORD with password and flags`() {
        val command = translator.translate(
            CommandType.RESET_PASSWORD,
            CommandParams(newPassword = "TempPass123", resetPasswordFlags = listOf("REQUIRE_ENTRY")),
        )

        assertEquals("RESET_PASSWORD", command.type)
        assertEquals("TempPass123", command.newPassword)
        assertEquals(listOf("REQUIRE_ENTRY"), command.resetPasswordFlags)
    }

    @Test
    fun `translates WIPE with wipe data flags`() {
        val command = translator.translate(CommandType.WIPE, CommandParams(wipeDataFlags = listOf("WIPE_EXTERNAL_STORAGE")))

        assertEquals("WIPE", command.type)
        assertEquals(listOf("WIPE_EXTERNAL_STORAGE"), command.wipeParams.wipeDataFlags)
    }

    @Test
    fun `translates CLEAR_APP_DATA with the target package names`() {
        val command = translator.translate(
            CommandType.CLEAR_APP_DATA,
            CommandParams(clearAppsDataPackageNames = listOf("com.example.app")),
        )

        assertEquals("CLEAR_APP_DATA", command.type)
        assertEquals(listOf("com.example.app"), command.clearAppsDataParams.packageNames)
    }

    @Test
    fun `translates REQUEST_DEVICE_INFO with the requested info type`() {
        val command = translator.translate(
            CommandType.REQUEST_DEVICE_INFO,
            CommandParams(requestDeviceInfoType = "DEVICE_SETTINGS"),
        )

        assertEquals("REQUEST_DEVICE_INFO", command.type)
        assertEquals("DEVICE_SETTINGS", command.requestDeviceInfoParams.deviceInfo)
    }
}
