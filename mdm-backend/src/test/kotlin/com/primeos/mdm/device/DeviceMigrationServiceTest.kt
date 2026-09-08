package com.primeos.mdm.device

import com.google.api.services.androidmanagement.v1.model.MigrationToken
import com.primeos.mdm.admin.AdminAccessGuard
import com.primeos.mdm.enterprise.AndroidManagementService
import com.primeos.mdm.enterprise.GmsEnterprise
import com.primeos.mdm.enterprise.GmsEnterpriseRepository
import com.primeos.mdm.enterprise.NoGmsEnterpriseException
import com.primeos.mdm.organization.Organization
import com.primeos.mdm.policy.Policy
import com.primeos.mdm.policy.PolicyNotFoundException
import com.primeos.mdm.policy.PolicyRepository
import com.primeos.mdm.policy.PolicySyncResult
import com.primeos.mdm.policy.PolicyTranslationService
import com.primeos.mdm.policy.translator.CustomDpcPolicyPayload
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import java.util.Optional
import java.util.UUID

class DeviceMigrationServiceTest {

    private val deviceRepository = mock(DeviceRepository::class.java)
    private val gmsEnterpriseRepository = mock(GmsEnterpriseRepository::class.java)
    private val policyRepository = mock(PolicyRepository::class.java)
    private val policyTranslationService = mock(PolicyTranslationService::class.java)
    private val androidManagementService = mock(AndroidManagementService::class.java)
    private val gmsMigrationTokenRepository = mock(GmsMigrationTokenRepository::class.java)

    private val service = DeviceMigrationService(
        deviceRepository,
        gmsEnterpriseRepository,
        policyRepository,
        policyTranslationService,
        androidManagementService,
        gmsMigrationTokenRepository,
        mock(AdminAccessGuard::class.java),
    )

    private val organizationId = UUID.randomUUID()
    private val organization = Organization(name = "Acme", slug = "acme").apply { id = organizationId }
    private val gmsEnterprise = GmsEnterprise(
        organization = organization,
        enterpriseName = "enterprises/LC00abc123",
        gcpProjectId = "test-project",
        serviceAccountSecretRef = "secrets/android-management-sa.json",
    )
    private val deviceId = UUID.randomUUID()
    private val device = Device(organization = organization, deviceType = DeviceType.NON_GMS, deviceUid = "dpc-1")
        .apply { id = deviceId }
    private val policyId = UUID.randomUUID()
    private val policy = Policy(organization = organization, name = "Migration Policy", definition = "{}").apply { id = policyId }

    @Test
    fun `mints a migration token using the app-supplied playDeviceId and playUserId`() {
        given(deviceRepository.findById(deviceId)).willReturn(Optional.of(device))
        given(gmsEnterpriseRepository.findByOrganizationId(organizationId)).willReturn(gmsEnterprise)
        given(policyRepository.findById(policyId)).willReturn(Optional.of(policy))
        given(policyTranslationService.syncPolicy(policyId)).willReturn(
            PolicySyncResult(
                customDpcPayload = CustomDpcPolicyPayload(),
                gmsPolicyName = "enterprises/LC00abc123/policies/$policyId",
            )
        )
        given(
            androidManagementService.createMigrationToken(
                enterpriseName = eq("enterprises/LC00abc123"),
                playDeviceId = eq("play-device-xyz"),
                playUserId = eq("play-user-abc"),
                policyName = eq("enterprises/LC00abc123/policies/$policyId"),
                ttlSeconds = eq(3600L),
                additionalData = eq(null),
            )
        ).willReturn(
            MigrationToken()
                .setName("enterprises/LC00abc123/migrationTokens/xyz")
                .setValue("RAWMIGRATIONTOKENVALUE")
                .setExpireTime("2026-09-08T10:00:00.000Z")
        )

        val result = service.createMigrationToken(
            deviceId,
            GmsMigrationTokenRequest(
                playDeviceId = "play-device-xyz",
                playUserId = "play-user-abc",
                policyId = policyId,
                ttlSeconds = 3600L,
            ),
        )

        assertEquals("RAWMIGRATIONTOKENVALUE", result.value)
        assertEquals("enterprises/LC00abc123/migrationTokens/xyz", result.name)

        val captor = argumentCaptor<GmsMigrationToken>()
        verify(gmsMigrationTokenRepository).save(captor.capture())
        assertEquals("RAWMIGRATIONTOKENVALUE", captor.firstValue.tokenValue)
        assertEquals("play-device-xyz", captor.firstValue.playDeviceId)
        assertEquals("play-user-abc", captor.firstValue.playUserId)
        assertEquals(deviceId, captor.firstValue.device.id)
        assertEquals(policyId, captor.firstValue.policy.id)
    }

    @Test
    fun `does not persist a migration token when Google rejects the request`() {
        given(gmsEnterpriseRepository.findByOrganizationId(organizationId)).willReturn(gmsEnterprise)
        given(deviceRepository.findById(deviceId)).willReturn(Optional.of(device))
        given(policyRepository.findById(policyId)).willReturn(Optional.of(policy))
        given(policyTranslationService.syncPolicy(policyId)).willReturn(
            PolicySyncResult(customDpcPayload = CustomDpcPolicyPayload(), gmsPolicyName = "enterprises/LC00abc123/policies/$policyId")
        )
        given(androidManagementService.createMigrationToken(any(), any(), any(), any(), anyOrNull(), anyOrNull()))
            .willThrow(RuntimeException("Google rejected it"))

        assertThrows(RuntimeException::class.java) {
            service.createMigrationToken(deviceId, GmsMigrationTokenRequest(playDeviceId = "x", playUserId = "y", policyId = policyId))
        }
        verify(gmsMigrationTokenRepository, never()).save(any())
    }

    @Test
    fun `listByOrganization maps every stored migration token to a summary`() {
        val token = GmsMigrationToken(
            organization = organization,
            device = device,
            policy = policy,
            playDeviceId = "play-device-xyz",
            playUserId = "play-user-abc",
            tokenValue = "RAWMIGRATIONTOKENVALUE",
        ).apply { id = UUID.randomUUID() }
        given(gmsMigrationTokenRepository.findByOrganizationIdOrderByCreatedAtDesc(organizationId)).willReturn(listOf(token))

        val summaries = service.listByOrganization(organizationId)

        assertEquals(1, summaries.size)
        assertEquals("RAWMIGRATIONTOKENVALUE", summaries[0].tokenValue)
        assertEquals(deviceId, summaries[0].deviceId)
        assertEquals(policyId, summaries[0].policyId)
        assertEquals("Migration Policy", summaries[0].policyName)
    }

    @Test
    fun `rejects an unknown device`() {
        given(deviceRepository.findById(deviceId)).willReturn(Optional.empty())

        assertThrows(DeviceNotFoundException::class.java) {
            service.createMigrationToken(deviceId, GmsMigrationTokenRequest(playDeviceId = "x", playUserId = "y", policyId = policyId))
        }
    }

    @Test
    fun `rejects an organization with no GMS enterprise yet`() {
        given(deviceRepository.findById(deviceId)).willReturn(Optional.of(device))
        given(gmsEnterpriseRepository.findByOrganizationId(organizationId)).willReturn(null)

        assertThrows(NoGmsEnterpriseException::class.java) {
            service.createMigrationToken(deviceId, GmsMigrationTokenRequest(playDeviceId = "x", playUserId = "y", policyId = policyId))
        }
    }

    @Test
    fun `rejects an unknown policy`() {
        given(deviceRepository.findById(deviceId)).willReturn(Optional.of(device))
        given(gmsEnterpriseRepository.findByOrganizationId(organizationId)).willReturn(gmsEnterprise)
        given(policyRepository.findById(policyId)).willReturn(Optional.empty())

        assertThrows(PolicyNotFoundException::class.java) {
            service.createMigrationToken(deviceId, GmsMigrationTokenRequest(playDeviceId = "x", playUserId = "y", policyId = policyId))
        }
    }

    @Test
    fun `rejects a policy from a different organization`() {
        val otherOrganization = Organization(name = "Other", slug = "other").apply { id = UUID.randomUUID() }
        val otherPolicy = Policy(organization = otherOrganization, name = "Other", definition = "{}").apply { id = policyId }
        given(deviceRepository.findById(deviceId)).willReturn(Optional.of(device))
        given(gmsEnterpriseRepository.findByOrganizationId(organizationId)).willReturn(gmsEnterprise)
        given(policyRepository.findById(policyId)).willReturn(Optional.of(otherPolicy))

        assertThrows(PolicyOrganizationMismatchException::class.java) {
            service.createMigrationToken(deviceId, GmsMigrationTokenRequest(playDeviceId = "x", playUserId = "y", policyId = policyId))
        }
    }
}
