package com.primeos.mdm.device

data class UnenrollDeviceRequest(
    val wipeDataFlags: List<String> = emptyList(),
    val wipeReasonMessage: String? = null,
)
