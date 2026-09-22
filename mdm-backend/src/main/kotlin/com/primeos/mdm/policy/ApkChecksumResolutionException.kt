package com.primeos.mdm.policy

class ApkChecksumResolutionException(apkUrl: String, reason: String) :
    RuntimeException("Could not compute a checksum for the APK at $apkUrl: $reason")
