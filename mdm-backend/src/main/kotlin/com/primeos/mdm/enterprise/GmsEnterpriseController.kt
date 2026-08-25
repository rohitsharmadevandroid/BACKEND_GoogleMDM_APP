package com.primeos.mdm.enterprise

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/organizations/{organizationId}/gms-enterprise")
class GmsEnterpriseController(
    private val gmsEnterpriseService: GmsEnterpriseService,
) {

    // An admin dashboard calls this, then opens the returned url in a
    // browser for the customer's Google Workspace/Play admin to complete.
    @PostMapping("/signup-url")
    fun startSignup(@PathVariable organizationId: UUID): Map<String, String> =
        mapOf("url" to gmsEnterpriseService.startSignup(organizationId))

    // Google redirects the admin's browser here after they finish sign-up.
    @GetMapping("/callback")
    fun completeSignup(
        @PathVariable organizationId: UUID,
        @RequestParam enterpriseToken: String,
    ): Map<String, String> {
        val enterprise = gmsEnterpriseService.completeSignup(organizationId, enterpriseToken)
        return mapOf("status" to "created", "enterpriseName" to enterprise.enterpriseName)
    }
}
