package com.primeos.mdm.policy

import com.primeos.mdm.admin.AdminAccessGuard
import com.primeos.mdm.enterprise.AndroidManagementService
import com.primeos.mdm.enterprise.GmsEnterpriseRepository
import com.primeos.mdm.policy.translator.AndroidManagementPolicyTranslator
import com.primeos.mdm.policy.translator.CustomDpcPolicyTranslator
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class PolicyTranslationService(
    private val policyRepository: PolicyRepository,
    private val policyDefinitionCodec: PolicyDefinitionCodec,
    private val androidManagementPolicyTranslator: AndroidManagementPolicyTranslator,
    private val customDpcPolicyTranslator: CustomDpcPolicyTranslator,
    private val androidManagementService: AndroidManagementService,
    private val gmsEnterpriseRepository: GmsEnterpriseRepository,
    private val adminAccessGuard: AdminAccessGuard,
) {

    // Renders both target formats from the internal definition. If the
    // policy's organization has a GMS enterprise, also pushes the
    // translated Google policy live via policies.patch; otherwise this
    // just renders both formats without touching the network - the custom
    // DPC payload has nowhere to be dispatched to yet (that's the non-GMS
    // sync protocol, a separate piece of work).
    @Transactional
    fun syncPolicy(policyId: UUID): PolicySyncResult {
        val policy = policyRepository.findById(policyId).orElseThrow { PolicyNotFoundException(policyId) }
        adminAccessGuard.requireOrganizationAccess(policy.organization.id!!)
        val definition = policyDefinitionCodec.decode(policy.definition)

        val customDpcPayload = customDpcPolicyTranslator.translate(definition)

        val gmsEnterprise = gmsEnterpriseRepository.findByOrganizationId(policy.organization.id!!)
        val pushedPolicyName = gmsEnterprise?.let { enterprise ->
            val googlePolicy = androidManagementPolicyTranslator.translate(definition)
            val policyName = "${enterprise.enterpriseName}/policies/${policy.id}"
            androidManagementService.upsertPolicy(policyName, googlePolicy)
            policyName
        }

        return PolicySyncResult(customDpcPayload = customDpcPayload, gmsPolicyName = pushedPolicyName)
    }
}
