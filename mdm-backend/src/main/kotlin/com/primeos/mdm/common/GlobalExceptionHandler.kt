package com.primeos.mdm.common

import com.primeos.mdm.admin.AdminUserNotFoundException
import com.primeos.mdm.admin.AdminUserRoleOrganizationMismatchException
import com.primeos.mdm.admin.DuplicateEmailException
import com.primeos.mdm.admin.InvalidCredentialsException
import com.primeos.mdm.admin.OrganizationAccessDeniedException
import com.primeos.mdm.command.CommandNotFoundException
import com.primeos.mdm.device.DeviceNotFoundException
import com.primeos.mdm.device.InvalidDeviceStateException
import com.primeos.mdm.device.PolicyOrganizationMismatchException
import com.primeos.mdm.enrollment.EnrollmentTokenNotFoundException
import com.primeos.mdm.enrollment.GmsPolicyOrganizationMismatchException
import com.primeos.mdm.enrollment.InvalidEnrollmentTokenException
import com.primeos.mdm.enterprise.GmsEnterpriseAlreadyExistsException
import com.primeos.mdm.enterprise.NoGmsEnterpriseException
import com.primeos.mdm.enterprise.NoPendingGmsSignupException
import com.primeos.mdm.organization.DuplicateSlugException
import com.primeos.mdm.organization.OrganizationHasAdminUsersException
import com.primeos.mdm.organization.OrganizationNotFoundException
import com.primeos.mdm.policy.PolicyNotFoundException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

// None of the domain exceptions accumulated across this codebase (every
// *NotFoundException, Duplicate*Exception, Invalid*Exception, etc.) had a
// mapped HTTP status before this - they all fell through to a generic,
// unhelpful response. This surfaced as a real bug during live testing of
// admin auth: a bad login threw InvalidCredentialsException, which (with
// no status mapping) fell all the way to Spring Boot's default /error
// dispatch - which SecurityConfig's authenticated() catch-all was blocking,
// producing a blank 403 instead of any useful error for ANY unhandled
// exception in the app, not just login.
@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(
        OrganizationNotFoundException::class,
        PolicyNotFoundException::class,
        DeviceNotFoundException::class,
        CommandNotFoundException::class,
        AdminUserNotFoundException::class,
        EnrollmentTokenNotFoundException::class,
    )
    fun handleNotFound(ex: RuntimeException): ResponseEntity<Map<String, String?>> =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(mapOf("error" to ex.message))

    @ExceptionHandler(
        DuplicateSlugException::class,
        DuplicateEmailException::class,
        GmsEnterpriseAlreadyExistsException::class,
        OrganizationHasAdminUsersException::class,
    )
    fun handleConflict(ex: RuntimeException): ResponseEntity<Map<String, String?>> =
        ResponseEntity.status(HttpStatus.CONFLICT).body(mapOf("error" to ex.message))

    @ExceptionHandler(
        InvalidDeviceStateException::class,
        InvalidEnrollmentTokenException::class,
        NoGmsEnterpriseException::class,
        NoPendingGmsSignupException::class,
        PolicyOrganizationMismatchException::class,
        AdminUserRoleOrganizationMismatchException::class,
        GmsPolicyOrganizationMismatchException::class,
    )
    fun handleBadRequest(ex: RuntimeException): ResponseEntity<Map<String, String?>> =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(mapOf("error" to ex.message))

    @ExceptionHandler(InvalidCredentialsException::class)
    fun handleUnauthorized(ex: InvalidCredentialsException): ResponseEntity<Map<String, String?>> =
        ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(mapOf("error" to ex.message))

    @ExceptionHandler(OrganizationAccessDeniedException::class)
    fun handleOrganizationAccessDenied(ex: OrganizationAccessDeniedException): ResponseEntity<Map<String, String?>> =
        ResponseEntity.status(HttpStatus.FORBIDDEN).body(mapOf("error" to ex.message))

    // Catch-all so a genuine bug still returns a real status/message
    // instead of leaking as a blank response - this is the last line of
    // defense; SecurityConfig's /error permitAll + 401 entry point below
    // handle exceptions thrown outside controller dispatch entirely
    // (filters, etc.), which this handler can never see.
    @ExceptionHandler(Exception::class)
    fun handleUnexpected(ex: Exception): ResponseEntity<Map<String, String?>> =
        ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(mapOf("error" to (ex.message ?: ex.javaClass.simpleName)))
}
