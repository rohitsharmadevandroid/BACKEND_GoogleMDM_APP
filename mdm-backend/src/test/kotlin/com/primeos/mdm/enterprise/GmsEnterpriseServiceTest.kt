package com.primeos.mdm.enterprise

import com.google.api.services.androidmanagement.v1.model.Enterprise
import com.google.api.services.androidmanagement.v1.model.SignupUrl
import com.primeos.mdm.admin.AdminAccessGuard
import com.primeos.mdm.organization.Organization
import com.primeos.mdm.organization.OrganizationNotFoundException
import com.primeos.mdm.organization.OrganizationRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyString
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import java.util.Optional
import java.util.UUID

class GmsEnterpriseServiceTest {

    private val androidManagementService = mock(AndroidManagementService::class.java)
    private val organizationRepository = mock(OrganizationRepository::class.java)
    private val gmsEnterpriseRepository = mock(GmsEnterpriseRepository::class.java)
    private val gmsEnterpriseSignupRepository = mock(GmsEnterpriseSignupRepository::class.java)

    private lateinit var service: GmsEnterpriseService

    private val organizationId = UUID.randomUUID()
    private val organization = Organization(name = "Acme", slug = "acme")

    @BeforeEach
    fun setUp() {
        service = GmsEnterpriseService(
            androidManagementService,
            organizationRepository,
            gmsEnterpriseRepository,
            gmsEnterpriseSignupRepository,
            "test-project",
            "http://localhost:8080",
            "secrets/android-management-sa.json",
            mock(AdminAccessGuard::class.java),
        )
        given(organizationRepository.findById(organizationId)).willReturn(Optional.of(organization))
    }

    @Test
    fun `startSignup persists the pending signup and returns the url`() {
        given(gmsEnterpriseRepository.findByOrganizationId(organizationId)).willReturn(null)
        given(androidManagementService.createSignupUrl(anyString())).willReturn(
            SignupUrl().setName("signupUrls/abc123").setUrl("https://play.google.com/work/signup/abc123")
        )

        val url = service.startSignup(organizationId)

        assertEquals("https://play.google.com/work/signup/abc123", url)
        verify(gmsEnterpriseSignupRepository).save(any(GmsEnterpriseSignup::class.java))
    }

    @Test
    fun `startSignup rejects an unknown organization`() {
        val unknownId = UUID.randomUUID()
        given(organizationRepository.findById(unknownId)).willReturn(Optional.empty())

        assertThrows(OrganizationNotFoundException::class.java) {
            service.startSignup(unknownId)
        }
    }

    @Test
    fun `startSignup rejects an organization that already has a GMS enterprise`() {
        given(gmsEnterpriseRepository.findByOrganizationId(organizationId)).willReturn(
            GmsEnterprise(
                organization = organization,
                enterpriseName = "enterprises/LC00existing",
                gcpProjectId = "test-project",
                serviceAccountSecretRef = "secrets/android-management-sa.json",
            )
        )

        assertThrows(GmsEnterpriseAlreadyExistsException::class.java) {
            service.startSignup(organizationId)
        }
    }

    @Test
    fun `completeSignup rejects when there is no pending signup`() {
        given(gmsEnterpriseSignupRepository.findFirstByOrganizationIdAndConsumedAtIsNullOrderByCreatedAtDesc(organizationId))
            .willReturn(null)

        assertThrows(NoPendingGmsSignupException::class.java) {
            service.completeSignup(organizationId, "some-enterprise-token")
        }
    }

    @Test
    fun `completeSignup pairs the pending signupUrlName with the callback token and persists the enterprise`() {
        val pendingSignup = GmsEnterpriseSignup(organization = organization, signupUrlName = "signupUrls/abc123")
        given(gmsEnterpriseSignupRepository.findFirstByOrganizationIdAndConsumedAtIsNullOrderByCreatedAtDesc(organizationId))
            .willReturn(pendingSignup)
        given(
            androidManagementService.createEnterprise(
                signupUrlName = "signupUrls/abc123",
                enterpriseToken = "the-token",
                displayName = "Acme",
            )
        ).willReturn(Enterprise().setName("enterprises/LC00abc123"))
        given(gmsEnterpriseRepository.save(any(GmsEnterprise::class.java))).willAnswer { it.arguments[0] }

        val result = service.completeSignup(organizationId, "the-token")

        assertEquals("enterprises/LC00abc123", result.enterpriseName)
        verify(gmsEnterpriseSignupRepository).save(pendingSignup)
    }
}
