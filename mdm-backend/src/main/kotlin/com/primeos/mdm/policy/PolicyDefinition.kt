package com.primeos.mdm.policy

// Vendor-neutral internal policy shape - this is what an admin actually
// edits, and what the two translators (AndroidManagementPolicyTranslator,
// CustomDpcPolicyTranslator) each derive their own target format from.
// Deliberately a small, real subset (not full parity with Android
// Management API's 100+ policy fields) covering the concerns named up
// front: app restrictions, wifi config, password/lock/wipe, kiosk mode,
// and simple on/off device restrictions (camera, factory reset, screen
// capture, USB file transfer, safe boot, add user, outgoing calls, SMS).
data class PolicyDefinition(
    val passwordPolicy: PasswordPolicy? = null,
    val cameraDisabled: Boolean = false,
    val factoryResetDisabled: Boolean = false,
    val screenCaptureDisabled: Boolean = false,
    val usbFileTransferDisabled: Boolean = false,
    val safeBootDisabled: Boolean = false,
    val addUserDisabled: Boolean = false,
    val outgoingCallsDisabled: Boolean = false,
    val smsDisabled: Boolean = false,
    val kioskMode: KioskModeConfig? = null,
    val appRestrictions: List<AppRestriction> = emptyList(),
    val wifiConfig: WifiConfig? = null,
)

data class PasswordPolicy(
    val minLength: Int? = null,
    val requireAlphanumeric: Boolean = false,
    val maxFailedAttemptsBeforeWipe: Int? = null,
)

data class KioskModeConfig(
    val enabled: Boolean = false,
    val allowedPackageNames: List<String> = emptyList(),
)

enum class AppInstallType { REQUIRED, BLOCKED, AVAILABLE }

data class AppRestriction(
    val packageName: String,
    val installType: AppInstallType,
)

enum class WifiSecurityType { OPEN, WPA2_PSK }

data class WifiConfig(
    val ssid: String,
    val securityType: WifiSecurityType,
    val password: String? = null,
    val hidden: Boolean = false,
)
