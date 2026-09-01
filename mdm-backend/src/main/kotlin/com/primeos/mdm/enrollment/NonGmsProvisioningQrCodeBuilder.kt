package com.primeos.mdm.enrollment

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

// Builds the REAL Android Device Owner QR provisioning payload - the
// android.app.extra.PROVISIONING_* extras the OS setup wizard reads when
// this QR is scanned at the "tap 6 times" welcome-screen flow on a
// factory-reset device, as opposed to the placeholder
// {"enrollmentToken":"..."} NonGmsEnrollmentTokenService falls back to.
//
// Needs three facts only the DPC app's own build knows - its
// DeviceAdminReceiver's exact ComponentName, a public HTTPS URL a
// factory-reset device (no app installed yet) can download the APK from,
// and the base64 SHA-256 checksum of the APK's signing certificate. All
// three come from config (blank = "not configured yet"), matching the
// mdm.gcp.pubsub-subscription-id pattern elsewhere in this app, rather than
// being hardcoded here - they belong to a separate Android project this
// backend doesn't build or have visibility into.
@Component
class NonGmsProvisioningQrCodeBuilder(
    private val objectMapper: ObjectMapper,
    @Value("\${mdm.dpc.device-admin-component-name:}")
    private val deviceAdminComponentName: String,
    @Value("\${mdm.dpc.apk-download-url:}")
    private val apkDownloadUrl: String,
    @Value("\${mdm.dpc.apk-signature-checksum:}")
    private val apkSignatureChecksum: String,
) {

    fun isConfigured(): Boolean =
        deviceAdminComponentName.isNotBlank() && apkDownloadUrl.isNotBlank() && apkSignatureChecksum.isNotBlank()

    // Returns null (caller falls back to the placeholder) until all three
    // config values are set - a real-shaped-but-wrong QR would fail
    // provisioning with a cryptic device-side error instead of the
    // placeholder's obvious "this isn't a real provisioning QR yet".
    fun build(tokenValue: String): String? {
        if (!isConfigured()) return null

        val payload = linkedMapOf<String, Any>(
            "android.app.extra.PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME" to deviceAdminComponentName,
            "android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION" to apkDownloadUrl,
            "android.app.extra.PROVISIONING_DEVICE_ADMIN_SIGNATURE_CHECKSUM" to apkSignatureChecksum,
            // Delivered back to the app's own DeviceAdminReceiver via
            // onProfileProvisioningComplete() once Device Owner is granted -
            // the "enrollmentToken" key here was agreed with the app side to
            // match DpcEnrollRequest.enrollmentToken exactly.
            "android.app.extra.PROVISIONING_ADMIN_EXTRAS_BUNDLE" to mapOf("enrollmentToken" to tokenValue),
        )
        return objectMapper.writeValueAsString(payload)
    }
}
