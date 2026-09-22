package com.primeos.mdm.policy

import org.springframework.stereotype.Service
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest

// Computes REQUIRED app restrictions' apkSha256 server-side from apkUrl
// instead of asking an admin to find/paste it - a real checksum an admin
// has to hunt down themselves is exactly the kind of manual step this
// codebase avoids everywhere else (see the GMS enrollment-token policyId
// fix earlier in this project's history, same motivation). The DPC app's
// own verify-before-install step (ApkInstaller, on the Android side) is
// unaffected - it still checks the APK it downloads against apkSha256,
// this only changes who computes that value.
@Service
open class ApkChecksumService {

    // Called once per REQUIRED+apkUrl entry when a policy is created/
    // updated (PolicyController, ahead of PolicyService's DB transaction -
    // this does real network I/O and must never run inside one). A
    // BLOCKED/AVAILABLE entry, or a REQUIRED one with no apkUrl, passes
    // through untouched - same as before this existed.
    fun resolve(definition: PolicyDefinition): PolicyDefinition {
        if (definition.appRestrictions.none { it.installType == AppInstallType.REQUIRED && it.apkUrl != null }) {
            return definition
        }
        return definition.copy(
            appRestrictions = definition.appRestrictions.map { restriction ->
                if (restriction.installType == AppInstallType.REQUIRED && restriction.apkUrl != null) {
                    restriction.copy(apkSha256 = computeSha256(restriction.apkUrl))
                } else {
                    restriction
                }
            }
        )
    }

    internal fun computeSha256(apkUrl: String): String = openStream(apkUrl).use { hashCapped(it, apkUrl) }

    // Split out purely so ApkChecksumServiceTest can substitute a fixed
    // stream instead of making a real network call - `open` for that one
    // reason, not a general extension point.
    internal open fun openStream(apkUrl: String): InputStream {
        val connection = URI.create(apkUrl).toURL().openConnection() as HttpURLConnection
        connection.connectTimeout = CONNECT_TIMEOUT_MS
        connection.readTimeout = READ_TIMEOUT_MS
        connection.instanceFollowRedirects = true
        connection.connect()
        val status = connection.responseCode
        if (status !in 200..299) {
            throw ApkChecksumResolutionException(apkUrl, "download failed with HTTP $status")
        }
        return connection.inputStream
    }

    internal fun hashCapped(input: InputStream, apkUrl: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(8192)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read == -1) break
            digest.update(buffer, 0, read)
            total += read
            if (total > MAX_APK_BYTES) {
                throw ApkChecksumResolutionException(apkUrl, "exceeds the ${MAX_APK_BYTES / (1024 * 1024)}MB limit for automatic checksum computation")
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val READ_TIMEOUT_MS = 60_000
        private const val MAX_APK_BYTES = 150L * 1024 * 1024
    }
}
