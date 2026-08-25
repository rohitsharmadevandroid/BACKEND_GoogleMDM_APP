package com.primeos.mdm.enterprise

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.io.File

// Opt-in: hits the real Android Management API using whatever mdm.gcp.*
// config/credentials are present. Skips itself (assumeTrue, not a failure)
// when no service-account key exists, so it never breaks the build for
// someone without GCP credentials configured.
@SpringBootTest(classes = [AndroidManagementConfig::class, AndroidManagementService::class])
class AndroidManagementServiceIT {

    @Autowired
    private lateinit var androidManagementService: AndroidManagementService

    @Test
    fun `service account can call the Android Management API`() {
        assumeTrue(File("secrets/android-management-sa.json").exists(), "No service-account key present, skipping")

        val signupUrl = androidManagementService.createSignupUrl("https://example.com/enrollment/gms/callback")

        assertNotNull(signupUrl.name)
        assertNotNull(signupUrl.url)
    }
}
