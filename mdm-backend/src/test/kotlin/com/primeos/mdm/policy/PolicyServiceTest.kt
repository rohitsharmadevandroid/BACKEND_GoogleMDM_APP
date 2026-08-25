package com.primeos.mdm.policy

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.primeos.mdm.admin.AdminAccessGuard
import com.primeos.mdm.device.Device
import com.primeos.mdm.device.DeviceRepository
import com.primeos.mdm.device.DeviceType
import com.primeos.mdm.organization.Organization
import com.primeos.mdm.organization.OrganizationNotFoundException
import com.primeos.mdm.organization.OrganizationRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import java.util.Optional
import java.util.UUID

class PolicyServiceTest {

    private val organizationRepository = mock(OrganizationRepository::class.java)
    private val policyRepository = mock(PolicyRepository::class.java)
    private val policyRevisionRepository = mock(PolicyRevisionRepository::class.java)
    private val deviceRepository = mock(DeviceRepository::class.java)

    private val service = PolicyService(
        organizationRepository,
        policyRepository,
        policyRevisionRepository,
        PolicyDefinitionCodec(jacksonObjectMapper()),
        deviceRepository,
        mock(AdminAccessGuard::class.java),
    )

    private val organizationId = UUID.randomUUID()
    private val organization = Organization(name = "Acme", slug = "acme").apply { id = organizationId }

    @Test
    fun `creates a policy and encodes the definition`() {
        given(organizationRepository.findById(organizationId)).willReturn(Optional.of(organization))
        given(policyRepository.save(any())).willAnswer { (it.arguments[0] as Policy).apply { id = UUID.randomUUID() } }

        val response = service.create(
            organizationId,
            CreatePolicyRequest(name = "Default", definition = PolicyDefinition(cameraDisabled = true)),
        )

        assertEquals("Default", response.name)
        assertEquals(true, response.definition.cameraDisabled)
        assertEquals(1, response.version)
    }

    @Test
    fun `rejects creation for an unknown organization`() {
        val unknownId = UUID.randomUUID()
        given(organizationRepository.findById(unknownId)).willReturn(Optional.empty())

        assertThrows(OrganizationNotFoundException::class.java) {
            service.create(unknownId, CreatePolicyRequest(name = "Default"))
        }
    }

    @Test
    fun `update snapshots the old definition into a revision and bumps the version`() {
        val policyId = UUID.randomUUID()
        val policy = Policy(
            organization = organization,
            name = "Default",
            version = 1,
            definition = """{"cameraDisabled":false}""",
        ).apply { id = policyId }
        given(policyRepository.findById(policyId)).willReturn(Optional.of(policy))
        given(policyRepository.save(any())).willAnswer { it.arguments[0] as Policy }

        val response = service.update(
            policyId,
            UpdatePolicyRequest(definition = PolicyDefinition(cameraDisabled = true), changeNote = "lock down camera"),
        )

        assertEquals(2, response.version)
        assertEquals(true, response.definition.cameraDisabled)

        val revisionCaptor = argumentCaptor<PolicyRevision>()
        verify(policyRevisionRepository).save(revisionCaptor.capture())
        val savedRevision = revisionCaptor.firstValue
        assertEquals(1, savedRevision.version)
        assertEquals("""{"cameraDisabled":false}""", savedRevision.definition)
        assertEquals("lock down camera", savedRevision.changeNote)
    }

    @Test
    fun `rejects updating an unknown policy`() {
        val unknownId = UUID.randomUUID()
        given(policyRepository.findById(unknownId)).willReturn(Optional.empty())

        assertThrows(PolicyNotFoundException::class.java) {
            service.update(unknownId, UpdatePolicyRequest(definition = PolicyDefinition()))
        }
    }

    @Test
    fun `delete deactivates the policy without removing it`() {
        val policyId = UUID.randomUUID()
        val policy = Policy(organization = organization, name = "Default", definition = "{}").apply { id = policyId }
        given(policyRepository.findById(policyId)).willReturn(Optional.of(policy))
        given(policyRepository.save(any())).willAnswer { it.arguments[0] as Policy }

        val response = service.delete(policyId)

        assertEquals(false, response.isActive)
        assertEquals(false, policy.isActive)
    }

    @Test
    fun `delete is idempotent for an already-inactive policy`() {
        val policyId = UUID.randomUUID()
        val policy = Policy(organization = organization, name = "Default", definition = "{}", isActive = false).apply { id = policyId }
        given(policyRepository.findById(policyId)).willReturn(Optional.of(policy))
        given(policyRepository.save(any())).willAnswer { it.arguments[0] as Policy }

        val response = service.delete(policyId)

        assertEquals(false, response.isActive)
    }

    @Test
    fun `delete unassigns the policy from every device currently using it`() {
        val policyId = UUID.randomUUID()
        val policy = Policy(organization = organization, name = "Default", definition = "{}").apply { id = policyId }
        val device1 = Device(organization = organization, deviceType = DeviceType.NON_GMS, deviceUid = "dpc-1", policy = policy)
        val device2 = Device(organization = organization, deviceType = DeviceType.NON_GMS, deviceUid = "dpc-2", policy = policy)
        given(policyRepository.findById(policyId)).willReturn(Optional.of(policy))
        given(policyRepository.save(any())).willAnswer { it.arguments[0] as Policy }
        given(deviceRepository.findByPolicyId(policyId)).willReturn(listOf(device1, device2))
        given(deviceRepository.saveAll<Device>(any())).willAnswer { it.arguments[0] }

        service.delete(policyId)

        assertNull(device1.policy)
        assertNull(device2.policy)
    }

    @Test
    fun `rejects deleting an unknown policy`() {
        val unknownId = UUID.randomUUID()
        given(policyRepository.findById(unknownId)).willReturn(Optional.empty())

        assertThrows(PolicyNotFoundException::class.java) {
            service.delete(unknownId)
        }
    }
}
