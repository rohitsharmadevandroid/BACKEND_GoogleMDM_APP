package com.primeos.mdm.dpc

import com.primeos.mdm.command.CommandStatus

data class DpcCommandAckRequest(
    val status: CommandStatus,
    val errorMessage: String? = null,
    // Only meaningful for REQUEST_DEVICE_INFO today - whatever the DPC
    // collected for the requestDeviceInfoType it was asked for, as plain
    // key/value strings (e.g. {"imei": "...", "batteryLevel": "84"}).
    // Free-form on purpose: different requestDeviceInfoType values need
    // different keys, and this protocol has no per-type schema to enforce.
    val resultData: Map<String, String>? = null,
)
