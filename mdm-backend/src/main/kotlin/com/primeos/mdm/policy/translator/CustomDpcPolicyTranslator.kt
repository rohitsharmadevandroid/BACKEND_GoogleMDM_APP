package com.primeos.mdm.policy.translator

import com.primeos.mdm.policy.PolicyDefinition
import org.springframework.stereotype.Component

@Component
class CustomDpcPolicyTranslator {

    fun translate(definition: PolicyDefinition): CustomDpcPolicyPayload = CustomDpcPolicyPayload(
        password = definition.passwordPolicy?.let {
            CustomDpcPasswordPolicy(
                minimumLength = it.minLength,
                quality = if (it.requireAlphanumeric) "PASSWORD_QUALITY_ALPHANUMERIC" else "PASSWORD_QUALITY_SOMETHING",
                maxFailedAttemptsBeforeWipe = it.maxFailedAttemptsBeforeWipe,
            )
        },
        cameraDisabled = definition.cameraDisabled,
        factoryResetDisabled = definition.factoryResetDisabled,
        screenCaptureDisabled = definition.screenCaptureDisabled,
        usbFileTransferDisabled = definition.usbFileTransferDisabled,
        safeBootDisabled = definition.safeBootDisabled,
        addUserDisabled = definition.addUserDisabled,
        outgoingCallsDisabled = definition.outgoingCallsDisabled,
        smsDisabled = definition.smsDisabled,
        kioskMode = definition.kioskMode?.let {
            CustomDpcKioskMode(enabled = it.enabled, allowedPackageNames = it.allowedPackageNames)
        },
        appRestrictions = definition.appRestrictions.map {
            CustomDpcAppRestriction(
                packageName = it.packageName,
                installType = it.installType.name,
                apkUrl = it.apkUrl,
                apkSha256 = it.apkSha256,
            )
        },
        wifi = definition.wifiConfig?.let {
            CustomDpcWifiConfig(
                ssid = it.ssid,
                securityType = it.securityType.name,
                password = it.password,
                hidden = it.hidden,
            )
        },
    )
}
