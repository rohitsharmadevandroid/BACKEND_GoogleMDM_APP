package com.primeos.mdm.admin

import com.primeos.mdm.organization.OrganizationNotFoundException
import com.primeos.mdm.organization.OrganizationRepository
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class AdminUserService(
    private val adminUserRepository: AdminUserRepository,
    private val organizationRepository: OrganizationRepository,
    private val passwordEncoder: PasswordEncoder,
    private val adminAccessGuard: AdminAccessGuard,
) {

    // SUPER_ADMIN.organizationId must be null (platform-level, per
    // AdminAccessGuard.requireOrganizationAccess treating a null org as
    // "can touch anything"); ORG_ADMIN/ORG_VIEWER must have one, since both
    // roles are meaningless without an org to scope to and the dashboard's
    // very first call after login (loading the Devices list) uses
    // state.activeOrgId verbatim in the URL - with no org, that call goes
    // out as literally /api/organizations/null/devices and 500s on UUID
    // parsing. Nothing enforced this before; a real test account got
    // created as ORG_ADMIN with no org and broke exactly this way.
    @Transactional
    fun create(request: CreateAdminUserRequest): AdminUserResponse {
        if (adminUserRepository.findByEmail(request.email) != null) {
            throw DuplicateEmailException(request.email)
        }
        when (request.role) {
            AdminRole.SUPER_ADMIN -> if (request.organizationId != null) {
                throw AdminUserRoleOrganizationMismatchException(
                    "SUPER_ADMIN accounts are platform-level and must not be assigned to an organization"
                )
            }
            AdminRole.ORG_ADMIN, AdminRole.ORG_VIEWER -> if (request.organizationId == null) {
                throw AdminUserRoleOrganizationMismatchException(
                    "${request.role} accounts must be assigned to an organization"
                )
            }
        }

        val organization = request.organizationId?.let { organizationId ->
            organizationRepository.findById(organizationId).orElseThrow { OrganizationNotFoundException(organizationId) }
        }

        val adminUser = adminUserRepository.save(
            AdminUser(
                organization = organization,
                email = request.email,
                passwordHash = passwordEncoder.encode(request.password),
                role = request.role,
            )
        )

        return adminUser.toResponse()
    }

    // Platform-wide - SUPER_ADMIN-only at the SecurityConfig role level, so
    // no AdminAccessGuard call needed here (there's no single organization
    // to check access against anyway - this lists every org's admins plus
    // platform-level ones).
    @Transactional(readOnly = true)
    fun list(): List<AdminUserResponse> = adminUserRepository.findAll().map { it.toResponse() }

    @Transactional(readOnly = true)
    fun listByOrganization(organizationId: UUID): List<AdminUserResponse> {
        adminAccessGuard.requireOrganizationAccess(organizationId)
        return adminUserRepository.findByOrganizationId(organizationId).map { it.toResponse() }
    }

    // SUPER_ADMIN-only at the SecurityConfig role level, same as create()
    // and list() - deliberately not org-scoped, so there's no need to
    // handle the edge case of an org-scoped caller targeting a platform
    // super-admin (who has no organization to check against at all).
    @Transactional
    fun deactivate(adminUserId: UUID): AdminUserResponse {
        val adminUser = adminUserRepository.findById(adminUserId).orElseThrow { AdminUserNotFoundException(adminUserId) }
        adminUser.isActive = false
        return adminUserRepository.save(adminUser).toResponse()
    }

    private fun AdminUser.toResponse() = AdminUserResponse(
        id = id!!,
        email = email,
        role = role,
        organizationId = organization?.id,
        isActive = isActive,
    )
}
