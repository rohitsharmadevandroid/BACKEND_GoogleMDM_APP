package com.primeos.mdm.organization

import com.primeos.mdm.admin.AdminAccessGuard
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class OrganizationService(
    private val organizationRepository: OrganizationRepository,
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
}
