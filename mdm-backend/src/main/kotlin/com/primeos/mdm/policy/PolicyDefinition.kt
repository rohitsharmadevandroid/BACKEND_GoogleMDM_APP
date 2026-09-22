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

// apkUrl/apkSha256 are only meaningful for REQUIRED on a non-GMS device -
// GMS force-installs via Google's own Managed Google Play pipeline and
// never looks at these; the custom DPC has no equivalent, so this is the
// only way it can silently install an app (mirrors the same
// download-URL-plus-checksum pattern already used for the DPC's own APK,
// see MDM_DPC_APK_DOWNLOAD_URL/MDM_DPC_APK_SIGNATURE_CHECKSUM). Left null,
// REQUIRED on non-GMS stays exactly what it was before: a label the DPC
// can't act on.
data class AppRestriction(
    val packageName: String,
    val installType: AppInstallType,
    val apkUrl: String? = null,
    val apkSha256: String? = null,
)

enum class WifiSecurityType { OPEN, WPA2_PSK }

data class WifiConfig(
    val ssid: String,
    val securityType: WifiSecurityType,
    val password: String? = null,
    val hidden: Boolean = false,
)
