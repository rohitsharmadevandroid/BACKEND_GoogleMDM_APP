package com.primeos.mdm.policy

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.security.MessageDigest

class ApkChecksumServiceTest {

    // Substitutes a fixed in-memory stream for the real network call -
    // openStream() is the one seam this class exposes for exactly that,
    // see its kdoc.
    private class FakeApkChecksumService(private val bytes: ByteArray) : ApkChecksumService() {
        override fun openStream(apkUrl: String): InputStream = ByteArrayInputStream(bytes)
    }

    private fun realSha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    @Test
    fun `resolve computes a real sha256 for a REQUIRED entry with an apkUrl`() {
        val apkBytes = "fake apk contents".toByteArray()
        val service = FakeApkChecksumService(apkBytes)
        val definition = PolicyDefinition(
            appRestrictions = listOf(
                AppRestriction("com.example.required", AppInstallType.REQUIRED, apkUrl = "https://example.com/app.apk"),
            )
        )

        val resolved = service.resolve(definition)

        assertEquals(realSha256(apkBytes), resolved.appRestrictions.single().apkSha256)
    }

    @Test
    fun `resolve ignores a client-supplied apkSha256 and always recomputes it`() {
        val apkBytes = "fake apk contents".toByteArray()
        val service = FakeApkChecksumService(apkBytes)
        val definition = PolicyDefinition(
            appRestrictions = listOf(
                AppRestriction("com.example.required", AppInstallType.REQUIRED, apkUrl = "https://example.com/app.apk", apkSha256 = "stale-or-fake-value"),
            )
        )

        val resolved = service.resolve(definition)

        assertEquals(realSha256(apkBytes), resolved.appRestrictions.single().apkSha256)
    }

    @Test
    fun `resolve leaves BLOCKED and AVAILABLE entries untouched`() {
        val service = FakeApkChecksumService(ByteArray(0))
        val definition = PolicyDefinition(
            appRestrictions = listOf(
                AppRestriction("com.example.blocked", AppInstallType.BLOCKED),
                AppRestriction("com.example.available", AppInstallType.AVAILABLE),
            )
        )

        val resolved = service.resolve(definition)

        assertEquals(definition.appRestrictions, resolved.appRestrictions)
    }

    @Test
    fun `resolve leaves a REQUIRED entry with no apkUrl untouched`() {
        val service = FakeApkChecksumService(ByteArray(0))
        val definition = PolicyDefinition(
            appRestrictions = listOf(AppRestriction("com.example.required", AppInstallType.REQUIRED))
        )

        val resolved = service.resolve(definition)

        assertNull(resolved.appRestrictions.single().apkSha256)
    }

    @Test
    fun `hashCapped rejects a stream larger than the configured limit`() {
        val service = FakeApkChecksumService(ByteArray(0))
        val oversized = object : InputStream() {
            override fun read(): Int = 0
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                b.fill(0, off, off + len)
                return len
            }
        }

        assertThrows(ApkChecksumResolutionException::class.java) {
            // Real cap is 150MB - reading a stream that never ends proves
            // the cap actually stops the loop rather than running forever,
            // without needing to allocate 150MB in the test itself.
            service.hashCapped(LimitedFakeStream(oversized, 200L * 1024 * 1024), "https://example.com/huge.apk")
        }
    }

    // Wraps an infinite stream and cuts it off after `limit` bytes, so the
    // "rejects oversized input" test terminates instead of spinning forever
    // if hashCapped's own cap check were ever broken.
    private class LimitedFakeStream(private val delegate: InputStream, private val limit: Long) : InputStream() {
        private var read = 0L
        override fun read(): Int = error("not used")
        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (read >= limit) return -1
            val n = delegate.read(b, off, len)
            read += n
            return n
        }
    }
}
