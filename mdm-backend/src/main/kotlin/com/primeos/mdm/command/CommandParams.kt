package com.primeos.mdm.command

// Vendor-neutral params per command type; only the fields relevant to the
// chosen CommandType need to be set. Same shape gets persisted as
// Command.payload for audit, and translated into the real Android
// Management API Command object.
data class CommandParams(
    val lockDurationSeconds: Long? = null,
    val newPassword: String? = null,
    val resetPasswordFlags: List<String> = emptyList(),
    val wipeDataFlags: List<String> = emptyList(),
    val clearAppsDataPackageNames: List<String> = emptyList(),
    // Passed straight through to Google's RequestDeviceInfoParams.deviceInfo.
    // Its valid enum values aren't verifiable via the client jar (it's a
    // plain String field there, not a typed enum) - confirm the exact
    // accepted value against a real device/response before relying on it.
    val requestDeviceInfoType: String? = null,
)
