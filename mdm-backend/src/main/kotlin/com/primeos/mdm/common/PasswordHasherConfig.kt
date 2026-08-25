package com.primeos.mdm.common

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder

// BCrypt (not the fast SHA-256 used for device API keys in
// DeviceCredentialService) - the opposite tradeoff applies here: admin
// passwords are human-chosen and low-entropy, so hashing needs to be
// deliberately slow and salted to resist offline brute force. A random
// 256-bit device key has no such weakness, which is why that path
// correctly uses a fast hash instead.
@Configuration
class PasswordHasherConfig {

    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()
}
