package com.primeos.mdm.policy

import com.primeos.mdm.admin.AdminAccessGuard
import com.primeos.mdm.device.DeviceRepository
import com.primeos.mdm.organization.OrganizationNotFoundException
import com.primeos.mdm.organization.OrganizationRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class PolicyService(
    private val organizationRepository: OrganizationRepository,
    private val policyRepository: PolicyRepository,
    private val policyRevisionRepository: PolicyRevisionRepository,
    private val policyDefinitionCodec: PolicyDefinitionCodec,
    private val deviceRepository: DeviceRepository,
    private val adminAccessGuard: AdminAccessGuard,
) {

    @Transactional
    fun create(organizationId: UUID, request: CreatePolicyRequest): PolicyResponse {
        adminAccessGuard.requireOrganizationAccess(organizationId)
        val organization = organizationRepository.findById(organizationId)
            .orElseThrow { OrganizationNotFoundException(organizationId) }

        val policy = policyRepository.save(
            Policy(
                organization = organization,
                name = request.name,
                description = request.description,
                definition = policyDefinitionCodec.encode(request.definition),
            )
        )
        return policy.toResponse(request.definition)
    }

    @Transactional(readOnly = true)
    fun get(policyId: UUID): PolicyResponse {
        val policy = policyRepository.findById(policyId).orElseThrow { PolicyNotFoundException(policyId) }
        adminAccessGuard.requireOrganizationAccess(policy.organization.id!!)
        return policy.toResponse(policyDefinitionCodec.decode(policy.definition))
    }

    @Transactional(readOnly = true)
    fun listByOrganization(organizationId: UUID): List<PolicyResponse> {
        adminAccessGuard.requireOrganizationAccess(organizationId)
        return policyRepository.findByOrganizationId(organizationId).map { it.toResponse(policyDefinitionCodec.decode(it.definition)) }
    }

    // Snapshots the OLD definition into policy_revisions before
    // overwriting it - that table has existed since step #2 but nothing
    // ever actually wrote to it until now.
    @Transactional
    fun update(policyId: UUID, request: UpdatePolicyRequest): PolicyResponse {
        val policy = policyRepository.findById(policyId).orElseThrow { PolicyNotFoundException(policyId) }
        adminAccessGuard.requireOrganizationAccess(policy.organization.id!!)

        policyRevisionRepository.save(
            PolicyRevision(
                policy = policy,
                version = policy.version,
                definition = policy.definition,
                changeNote = request.changeNote,
            )
        )

        policy.definition = policyDefinitionCodec.encode(request.definition)
        policy.version += 1
        val saved = policyRepository.save(policy)
        return saved.toResponse(request.definition)
    }

    // Soft delete only, matching the established pattern for referenced
    // master records (see DeviceLifecycleService, EnrollmentTokenRevocationService)
    // - a Policy can be referenced by Device.policy, so the row must stay in
    // place. Idempotent: deactivating an already-inactive policy just
    // re-saves the same state rather than erroring.
    //
    // Also unassigns this policy from every device currently pointing at
    // it, so DpcCheckInService.checkIn - which compares policy.version
    // against the device's lastPolicyVersionApplied - starts reporting
    // policyVersion: null on the device's next check-in instead of
    // silently continuing to serve a deleted policy forever (there'd be no
    // version change to detect otherwise, since the policy row itself never
    // changes once deleted).
    @Transactional
    fun delete(policyId: UUID): PolicyResponse {
        val policy = policyRepository.findById(policyId).orElseThrow { PolicyNotFoundException(policyId) }
        adminAccessGuard.requireOrganizationAccess(policy.organization.id!!)

        policy.isActive = false
        val saved = policyRepository.save(policy)

        val assignedDevices = deviceRepository.findByPolicyId(policyId)
        assignedDevices.forEach { it.policy = null }
        deviceRepository.saveAll(assignedDevices)

        return saved.toResponse(policyDefinitionCodec.decode(saved.definition))
    }

    private fun Policy.toResponse(definition: PolicyDefinition) = PolicyResponse(
        id = id!!,
        organizationId = organization.id!!,
        name = name,
        description = description,
        version = version,
        isActive = isActive,
        definition = definition,
    )
}
