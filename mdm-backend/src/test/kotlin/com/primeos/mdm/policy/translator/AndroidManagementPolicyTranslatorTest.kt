package com.primeos.mdm.policy.translator

import com.primeos.mdm.policy.AppInstallType
import com.primeos.mdm.policy.AppRestriction
import com.primeos.mdm.policy.KioskModeConfig
import com.primeos.mdm.policy.PasswordPolicy
import com.primeos.mdm.policy.PolicyDefinition
import com.primeos.mdm.policy.WifiConfig
import com.primeos.mdm.policy.WifiSecurityType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AndroidManagementPolicyTranslatorTest {

    private val translator = AndroidManagementPolicyTranslator()

    @Test
    fun `translates camera, factory reset, and password requirements`() {
        val definition = PolicyDefinition(
            passwordPolicy = PasswordPolicy(minLength = 8, requireAlphanumeric = true, maxFailedAttemptsBeforeWipe = 10),
            cameraDisabled = true,
            factoryResetDisabled = true,
        )

        val policy = translator.translate(definition)

        assertEquals(true, policy.cameraDisabled)
        assertEquals(true, policy.factoryResetDisabled)
        val passwordRequirements = policy.passwordPolicies.single()
        assertEquals(8, passwordRequirements.passwordMinimumLength)
        assertEquals("ALPHANUMERIC", passwordRequirements.passwordQuality)
        assertEquals(10, passwordRequirements.maximumFailedPasswordsForWipe)
    }

    @Test
    fun `translates the simple on-off device restrictions`() {
        val definition = PolicyDefinition(
            screenCaptureDisabled = true,
            usbFileTransferDisabled = true,
            safeBootDisabled = true,
            addUserDisabled = true,
            outgoingCallsDisabled = true,
            smsDisabled = true,
        )

        val policy = translator.translate(definition)

        assertEquals(true, policy.screenCaptureDisabled)
        assertEquals(true, policy.usbFileTransferDisabled)
        assertEquals(true, policy.safeBootDisabled)
        assertEquals(true, policy.addUserDisabled)
        assertEquals(true, policy.outgoingCallsDisabled)
        assertEquals(true, policy.smsDisabled)
    }

    @Test
    fun `translates app restrictions to Android Management install types`() {
        val definition = PolicyDefinition(
            appRestrictions = listOf(
                AppRestriction("com.example.required", AppInstallType.REQUIRED),
                AppRestriction("com.example.blocked", AppInstallType.BLOCKED),
                AppRestriction("com.example.available", AppInstallType.AVAILABLE),
            ),
        )

        val policy = translator.translate(definition)

        val byPackage = policy.applications.associateBy { it.packageName }
        assertEquals("FORCE_INSTALLED", byPackage["com.example.required"]?.installType)
        assertEquals("BLOCKED", byPackage["com.example.blocked"]?.installType)
        assertEquals("AVAILABLE", byPackage["com.example.available"]?.installType)
    }

    @Test
    fun `translates kiosk mode into kioskCustomLauncherEnabled and KIOSK-typed applications`() {
        val definition = PolicyDefinition(
            kioskMode = KioskModeConfig(enabled = true, allowedPackageNames = listOf("com.example.kiosk")),
        )

        val policy = translator.translate(definition)

        assertEquals(true, policy.kioskCustomLauncherEnabled)
        val kioskApp = policy.applications.single { it.packageName == "com.example.kiosk" }
        assertEquals("KIOSK", kioskApp.installType)
    }

    @Test
    fun `translates wifi config into an Open Network Configuration block`() {
        val definition = PolicyDefinition(
            wifiConfig = WifiConfig(ssid = "OfficeWifi", securityType = WifiSecurityType.WPA2_PSK, password = "secret123"),
        )

        val policy = translator.translate(definition)

        @Suppress("UNCHECKED_CAST")
        val networkConfigs = policy.openNetworkConfiguration["NetworkConfigurations"] as List<Map<String, Any>>
        val wifiBlock = networkConfigs.single()["WiFi"] as Map<*, *>
        assertEquals("OfficeWifi", wifiBlock["SSID"])
        assertEquals("WPA-PSK", wifiBlock["Security"])
        assertEquals("secret123", wifiBlock["Passphrase"])
        assertTrue(wifiBlock["AutoConnect"] as Boolean)
    }
}
