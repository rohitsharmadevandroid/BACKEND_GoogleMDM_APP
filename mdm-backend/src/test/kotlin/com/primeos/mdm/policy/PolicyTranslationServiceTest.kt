package com.primeos.mdm.policy

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.google.api.services.androidmanagement.v1.model.Policy as GooglePolicy
import com.primeos.mdm.admin.AdminAccessGuard
import com.primeos.mdm.enterprise.AndroidManagementService
import com.primeos.mdm.enterprise.GmsEnterprise
import com.primeos.mdm.enterprise.GmsEnterpriseRepository
import com.primeos.mdm.organization.Organization
import com.primeos.mdm.policy.translator.AndroidManagementPolicyTranslator
import com.primeos.mdm.policy.translator.CustomDpcPolicyTranslator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import java.util.Optional
import java.util.UUID

class PolicyTranslationServiceTest {

    private val policyRepository = mock(PolicyRepository::class.java)
    private val gmsEnterpriseRepository = mock(GmsEnterpriseRepository::class.java)
    private val androidManagementService = mock(AndroidManagementService::class.java)

    private val service = PolicyTranslationService(
        policyRepository,
        PolicyDefinitionCodec(jacksonObjectMapper()),
        AndroidManagementPolicyTranslator(),
        CustomDpcPolicyTranslator(),
        androidManagementService,
        gmsEnterpriseRepository,
        mock(AdminAccessGuard::class.java),
    )

    private val organizationId = UUID.randomUUID()
    private val organization = Organization(name = "Acme", slug = "acme").apply { id = organizationId }
    private val policyId = UUID.randomUUID()

    private fun policyWithDefinition(json: String) =
        Policy(organization = organization, name = "Default", definition = json).apply { id = policyId }

    @Test
    fun `pushes the translated policy to Google when the org has a GMS enterprise`() {
        val policy = policyWithDefinition("""{"cameraDisabled": true}""")
        given(policyRepository.findById(policyId)).willReturn(Optional.of(policy))
        given(gmsEnterpriseRepository.findByOrganizationId(organizationId)).willReturn(
            GmsEnterprise(
                organization = organization,
                enterpriseName = "enterprises/LC00abc123",
                gcpProjectId = "test-project",
                serviceAccountSecretRef = "secrets/android-management-sa.json",
            )
        )
        val expectedPolicyName = "enterprises/LC00abc123/policies/$policyId"
        given(androidManagementService.upsertPolicy(eq(expectedPolicyName), any<GooglePolicy>()))
            .willAnswer { it.arguments[1] }

        val result = service.syncPolicy(policyId)

        assertEquals(expectedPolicyName, result.gmsPolicyName)
        assertEquals(true, result.customDpcPayload.cameraDisabled)
        verify(androidManagementService).upsertPolicy(eq(expectedPolicyName), any<GooglePolicy>())
    }

    @Test
    fun `renders only the custom DPC payload when the org has no GMS enterprise`() {
        val policy = policyWithDefinition("""{"factoryResetDisabled": true}""")
        given(policyRepository.findById(policyId)).willReturn(Optional.of(policy))
        given(gmsEnterpriseRepository.findByOrganizationId(organizationId)).willReturn(null)

        val result = service.syncPolicy(policyId)

        assertNull(result.gmsPolicyName)
        assertEquals(true, result.customDpcPayload.factoryResetDisabled)
        verify(androidManagementService, never()).upsertPolicy(any(), any())
    }

    @Test
    fun `rejects an unknown policy`() {
        val unknownId = UUID.randomUUID()
        given(policyRepository.findById(unknownId)).willReturn(Optional.empty())

        assertThrows(PolicyNotFoundException::class.java) {
            service.syncPolicy(unknownId)
        }
    }
}
