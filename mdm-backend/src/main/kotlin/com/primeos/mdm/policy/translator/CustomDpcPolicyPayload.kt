package com.primeos.mdm.policy.translator

// Wire format for the custom (non-GMS) DPC app - our own protocol, so we
// have full design freedom here. Deliberately flat/simple, since unlike
// Google's policy JSON this doesn't need to satisfy any external schema
// (no ONC, no Android Management API enum vocabulary).
data class CustomDpcPolicyPayload(
    val password: CustomDpcPasswordPolicy? = null,
    val cameraDisabled: Boolean = false,
    val factoryResetDisabled: Boolean = false,
    val screenCaptureDisabled: Boolean = false,
    val usbFileTransferDisabled: Boolean = false,
    val safeBootDisabled: Boolean = false,
    val addUserDisabled: Boolean = false,
    val outgoingCallsDisabled: Boolean = false,
    val smsDisabled: Boolean = false,
    val kioskMode: CustomDpcKioskMode? = null,
    val appRestrictions: List<CustomDpcAppRestriction> = emptyList(),
    val wifi: CustomDpcWifiConfig? = null,
)

data class CustomDpcPasswordPolicy(
    val minimumLength: Int?,
    // Matches android.app.admin.DevicePolicyManager's PASSWORD_QUALITY_*
    // constant names, so the DPC app can resolve this by name instead of
    // us inventing a separate vocabulary it has to re-map.
    val quality: String,
    val maxFailedAttemptsBeforeWipe: Int?,
)

data class CustomDpcKioskMode(
    val enabled: Boolean,
    val allowedPackageNames: List<String>,
)

data class CustomDpcAppRestriction(
    val packageName: String,
    // "REQUIRED" | "BLOCKED" | "AVAILABLE" - the DPC app decides what to
    // actually do with each, since a non-GMS device has no Play EMM
    // integration to enforce this automatically.
    val installType: String,
)

data class CustomDpcWifiConfig(
    val ssid: String,
    val securityType: String, // "OPEN" | "WPA2_PSK"
    val password: String?,
    val hidden: Boolean,
)
