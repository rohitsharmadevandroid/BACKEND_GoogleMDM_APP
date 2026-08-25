package com.primeos.mdm.policy.translator

import com.primeos.mdm.policy.AppInstallType
import com.primeos.mdm.policy.AppRestriction
import com.primeos.mdm.policy.KioskModeConfig
import com.primeos.mdm.policy.PasswordPolicy
import com.primeos.mdm.policy.PolicyDefinition
import com.primeos.mdm.policy.WifiConfig
import com.primeos.mdm.policy.WifiSecurityType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class CustomDpcPolicyTranslatorTest {

    private val translator = CustomDpcPolicyTranslator()

    @Test
    fun `translates a fully populated definition`() {
        val definition = PolicyDefinition(
            passwordPolicy = PasswordPolicy(minLength = 6, requireAlphanumeric = true, maxFailedAttemptsBeforeWipe = 5),
            cameraDisabled = true,
            factoryResetDisabled = true,
            screenCaptureDisabled = true,
            usbFileTransferDisabled = true,
            safeBootDisabled = true,
            addUserDisabled = true,
            outgoingCallsDisabled = true,
            smsDisabled = true,
            kioskMode = KioskModeConfig(enabled = true, allowedPackageNames = listOf("com.example.kiosk")),
            appRestrictions = listOf(AppRestriction("com.example.app", AppInstallType.REQUIRED)),
            wifiConfig = WifiConfig(ssid = "OfficeWifi", securityType = WifiSecurityType.WPA2_PSK, password = "secret123"),
        )

        val payload = translator.translate(definition)

        assertEquals(6, payload.password?.minimumLength)
        assertEquals("PASSWORD_QUALITY_ALPHANUMERIC", payload.password?.quality)
        assertEquals(5, payload.password?.maxFailedAttemptsBeforeWipe)
        assertEquals(true, payload.cameraDisabled)
        assertEquals(true, payload.factoryResetDisabled)
        assertEquals(true, payload.screenCaptureDisabled)
        assertEquals(true, payload.usbFileTransferDisabled)
        assertEquals(true, payload.safeBootDisabled)
        assertEquals(true, payload.addUserDisabled)
        assertEquals(true, payload.outgoingCallsDisabled)
        assertEquals(true, payload.smsDisabled)
        assertEquals(listOf("com.example.kiosk"), payload.kioskMode?.allowedPackageNames)
        assertEquals("REQUIRED", payload.appRestrictions.single().installType)
        assertEquals("OfficeWifi", payload.wifi?.ssid)
        assertEquals("WPA2_PSK", payload.wifi?.securityType)
    }

    @Test
    fun `omits optional sections and defaults device restrictions to false when absent from the definition`() {
        val payload = translator.translate(PolicyDefinition())

        assertNull(payload.password)
        assertNull(payload.kioskMode)
        assertNull(payload.wifi)
        assertEquals(emptyList<Any>(), payload.appRestrictions)
        assertEquals(false, payload.cameraDisabled)
        assertEquals(false, payload.factoryResetDisabled)
        assertEquals(false, payload.screenCaptureDisabled)
        assertEquals(false, payload.usbFileTransferDisabled)
        assertEquals(false, payload.safeBootDisabled)
        assertEquals(false, payload.addUserDisabled)
        assertEquals(false, payload.outgoingCallsDisabled)
        assertEquals(false, payload.smsDisabled)
    }
}
