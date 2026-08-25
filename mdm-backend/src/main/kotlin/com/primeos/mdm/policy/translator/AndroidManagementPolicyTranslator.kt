package com.primeos.mdm.policy.translator

import com.google.api.services.androidmanagement.v1.model.ApplicationPolicy
import com.google.api.services.androidmanagement.v1.model.PasswordRequirements
import com.google.api.services.androidmanagement.v1.model.Policy as GooglePolicy
import com.primeos.mdm.policy.AppInstallType
import com.primeos.mdm.policy.PasswordPolicy
import com.primeos.mdm.policy.PolicyDefinition
import com.primeos.mdm.policy.WifiConfig
import com.primeos.mdm.policy.WifiSecurityType
import org.springframework.stereotype.Component

@Component
class AndroidManagementPolicyTranslator {

    fun translate(definition: PolicyDefinition): GooglePolicy {
        val policy = GooglePolicy()
            .setCameraDisabled(definition.cameraDisabled)
            .setFactoryResetDisabled(definition.factoryResetDisabled)
            .setScreenCaptureDisabled(definition.screenCaptureDisabled)
            .setUsbFileTransferDisabled(definition.usbFileTransferDisabled)
            .setSafeBootDisabled(definition.safeBootDisabled)
            .setAddUserDisabled(definition.addUserDisabled)
            .setOutgoingCallsDisabled(definition.outgoingCallsDisabled)
            .setSmsDisabled(definition.smsDisabled)

        definition.passwordPolicy?.let {
            policy.setPasswordPolicies(listOf(translatePassword(it)))
        }

        val applications = mutableListOf<ApplicationPolicy>()
        definition.appRestrictions.forEach { restriction ->
            applications += ApplicationPolicy()
                .setPackageName(restriction.packageName)
                .setInstallType(googleInstallType(restriction.installType))
        }
        definition.kioskMode?.let { kiosk ->
            policy.setKioskCustomLauncherEnabled(kiosk.enabled)
            kiosk.allowedPackageNames.forEach { packageName ->
                applications += ApplicationPolicy().setPackageName(packageName).setInstallType("KIOSK")
            }
        }
        policy.setApplications(applications)

        definition.wifiConfig?.let {
            policy.setOpenNetworkConfiguration(translateWifi(it))
        }

        return policy
    }

    private fun googleInstallType(installType: AppInstallType): String = when (installType) {
        AppInstallType.REQUIRED -> "FORCE_INSTALLED"
        AppInstallType.BLOCKED -> "BLOCKED"
        AppInstallType.AVAILABLE -> "AVAILABLE"
    }

    private fun translatePassword(password: PasswordPolicy): PasswordRequirements {
        val requirements = PasswordRequirements()
            .setPasswordQuality(if (password.requireAlphanumeric) "ALPHANUMERIC" else "SOMETHING")
            .setPasswordScope("SCOPE_DEVICE")
        if (password.minLength != null) {
            requirements.setPasswordMinimumLength(password.minLength)
        }
        if (password.maxFailedAttemptsBeforeWipe != null) {
            requirements.setMaximumFailedPasswordsForWipe(password.maxFailedAttemptsBeforeWipe)
        }
        return requirements
    }

    // Open Network Configuration (ONC) format, per Android Management
    // API's documented openNetworkConfiguration shape. This field is an
    // untyped Map<String,Object> on the client, so unlike the rest of this
    // translator it can't be checked against real method signatures -
    // confirm this actual shape against a real enrolled device before
    // relying on it in production.
    private fun translateWifi(wifi: WifiConfig): Map<String, Any> {
        val wifiBlock = mutableMapOf<String, Any>(
            "AutoConnect" to true,
            "SSID" to wifi.ssid,
            "HiddenSSID" to wifi.hidden,
            "Security" to if (wifi.securityType == WifiSecurityType.WPA2_PSK) "WPA-PSK" else "None",
        )
        wifi.password?.let { wifiBlock["Passphrase"] = it }

        return mapOf(
            "Type" to "UnencryptedConfiguration",
            "NetworkConfigurations" to listOf(
                mapOf(
                    "GUID" to "wifi-${wifi.ssid.hashCode()}",
                    "Name" to wifi.ssid,
                    "Type" to "WiFi",
                    "WiFi" to wifiBlock,
                )
            ),
        )
    }
}
