package com.primeos.mdm.admin

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

// Dev/test-only bootstrap for the very first SUPER_ADMIN. Production has no
// equivalent - see SETUP.md §5 - because POST /api/admin-users itself
// requires an existing SUPER_ADMIN JWT, so the very first account can only
// ever come from something like this. Gated entirely by
// mdm.test.bootstrap-admin.enabled (matchIfMissing = false, and not set
// anywhere in application.yml), the same env-var-driven pattern every other
// optional setting in this app already uses - so it takes a deliberate
// MDM_TEST_BOOTSTRAP_ADMIN_ENABLED=true to ever run; the production process
// never sets it and stays completely unaffected.
// Idempotent by design: re-running against an already-bootstrapped database
// (e.g. every backend restart) just finds the existing row and no-ops.
@Component
@ConditionalOnProperty("mdm.test.bootstrap-admin.enabled", havingValue = "true", matchIfMissing = false)
class TestAdminBootstrap(
    private val adminUserRepository: AdminUserRepository,
    private val adminUserService: AdminUserService,
    @Value("\${mdm.test.bootstrap-admin.email:test-admin@mdm.test}")
    private val email: String,
    @Value("\${mdm.test.bootstrap-admin.password:TestAdmin123!}")
    private val password: String,
) : ApplicationRunner {

    private val logger = LoggerFactory.getLogger(TestAdminBootstrap::class.java)

    override fun run(args: ApplicationArguments) {
        if (adminUserRepository.findByEmail(email) != null) {
            logger.info("Test admin bootstrap: '{}' already exists, skipping", email)
            return
        }

        // Goes through the same AdminUserService.create() every real admin
        // user is created through, so password hashing (BCryptPasswordEncoder)
        // and the SUPER_ADMIN/organization validation are identical to
        // production - nothing about auth is special-cased for this account.
        adminUserService.create(
            CreateAdminUserRequest(
                email = email,
                password = password,
                role = AdminRole.SUPER_ADMIN,
                organizationId = null,
            )
        )
        logger.warn("Test admin bootstrap: created SUPER_ADMIN '{}' - only runs when MDM_TEST_BOOTSTRAP_ADMIN_ENABLED=true, never use in production", email)
    }
}
