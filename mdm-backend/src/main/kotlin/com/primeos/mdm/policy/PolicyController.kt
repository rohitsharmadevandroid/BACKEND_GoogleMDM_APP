package com.primeos.mdm.policy

import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api")
class PolicyController(
    private val policyService: PolicyService,
    private val policyTranslationService: PolicyTranslationService,
) {

    @PostMapping("/organizations/{organizationId}/policies")
    fun create(@PathVariable organizationId: UUID, @RequestBody request: CreatePolicyRequest): PolicyResponse =
        policyService.create(organizationId, request)

    @GetMapping("/organizations/{organizationId}/policies")
    fun listByOrganization(@PathVariable organizationId: UUID): List<PolicyResponse> =
        policyService.listByOrganization(organizationId)

    @GetMapping("/policies/{policyId}")
    fun get(@PathVariable policyId: UUID): PolicyResponse = policyService.get(policyId)

    // Only updates our internal record (bumps version, snapshots the old
    // definition into policy_revisions) - it does NOT push to Google or
    // queue anything for non-GMS devices. That's the separate, deliberate
    // /sync step below, so edits can be drafted before going live.
    @PutMapping("/policies/{policyId}")
    fun update(@PathVariable policyId: UUID, @RequestBody request: UpdatePolicyRequest): PolicyResponse =
        policyService.update(policyId, request)

    @PostMapping("/policies/{policyId}/sync")
    fun sync(@PathVariable policyId: UUID): PolicySyncResult = policyTranslationService.syncPolicy(policyId)

    // Soft delete (isActive = false) - see PolicyService.delete for why the
    // row itself is never removed.
    @DeleteMapping("/policies/{policyId}")
    fun delete(@PathVariable policyId: UUID): PolicyResponse = policyService.delete(policyId)
}
