package com.primeos.mdm.device

import org.springframework.stereotype.Service
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

@Service
class DeviceCredentialService {

    private val secureRandom = SecureRandom()

    // Returns (rawKey, hash). rawKey is shown to the caller exactly once
    // and never persisted - only its hash is stored, so a DB leak alone
    // never yields a usable credential. SHA-256 (not bcrypt/scrypt) is the
    // right choice here specifically because the input is already 256 bits
    // of random entropy, not a human-chosen password - there's nothing a
    // slow hash protects against here that a fast one doesn't already.
    fun generate(): DeviceCredential {
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        val rawKey = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        return DeviceCredential(rawKey = rawKey, hash = hash(rawKey))
    }

    fun hash(rawKey: String): String {
        val digestBytes = MessageDigest.getInstance("SHA-256").digest(rawKey.toByteArray())
        return digestBytes.joinToString("") { "%02x".format(it) }
    }
}

data class DeviceCredential(val rawKey: String, val hash: String)
