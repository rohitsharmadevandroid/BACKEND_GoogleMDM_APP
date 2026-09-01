package com.primeos.mdm.organization

import com.primeos.mdm.admin.AdminAccessGuard
import com.primeos.mdm.admin.AdminUserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class OrganizationService(
    private val organizationRepository: OrganizationRepository,
    private val adminUserRepository: AdminUserRepository,
    private val adminAccessGuard: AdminAccessGuard,
) {

    // create/list are SUPER_ADMIN-only at the SecurityConfig role level
    // (an org-scoped admin has no existing org to check access against,
    // and listing every org doesn't fit the isolation model at all), so
    // neither needs an AdminAccessGuard call here.
    @Transactional
    fun create(request: CreateOrganizationRequest): Organization {
        if (organizationRepository.findBySlug(request.slug) != null) {
            throw DuplicateSlugException(request.slug)
        }
        return organizationRepository.save(Organization(name = request.name, slug = request.slug))
    }

    @Transactional(readOnly = true)
    fun get(organizationId: UUID): Organization {
        adminAccessGuard.requireOrganizationAccess(organizationId)
        return organizationRepository.findById(organizationId).orElseThrow { OrganizationNotFoundException(organizationId) }
    }

    @Transactional(readOnly = true)
    fun list(): List<Organization> = organizationRepository.findAll()

    // A real, hard delete - not the soft-delete pattern used everywhere
    // else in this app (Policy.isActive, EnrollmentToken.status=REVOKED,
    // Device.status=DELETED). Deliberate: an organization is the tenant
    // root, so "delete this org" only ever means "throw the whole test
    // tenant away", never "mark it inactive but keep the data around".
    //
    // Every child table (devices, policies, policy_revisions,
    // enrollment_tokens, commands, gms_enterprises, gms_enterprise_signups)
    // has organization_id FK'd with ON DELETE CASCADE - see V1-V12
    // migrations - so a single organizationRepository.delete() here is
    // enough; Postgres does the cascade, not this method. The one FK that's
    // deliberately NOT cascaded is admin_users.organization_id, which IS
    // ON DELETE CASCADE at the DB level too, but we refuse to reach it: an
    // admin_users row is a login credential, a different class of thing
    // than test data, so this guards against silently deleting someone's
    // account as a side effect of cleaning up devices/policies. SUPER_ADMIN
    // -only (see create/list above) - no AdminAccessGuard call needed.
    @Transactional
    fun delete(organizationId: UUID) {
        val organization = organizationRepository.findById(organizationId)
            .orElseThrow { OrganizationNotFoundException(organizationId) }

        val adminUsers = adminUserRepository.findByOrganizationId(organizationId)
        if (adminUsers.isNotEmpty()) {
            throw OrganizationHasAdminUsersException(organizationId, adminUsers.size)
        }

        organizationRepository.delete(organization)
    }
}
