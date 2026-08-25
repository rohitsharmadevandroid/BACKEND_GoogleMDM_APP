package com.primeos.mdm.device

import java.util.UUID

class DeviceNotFoundException(deviceId: UUID) : RuntimeException("No device with id $deviceId")
