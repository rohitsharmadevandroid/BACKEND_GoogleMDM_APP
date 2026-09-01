package com.primeos.mdm.organization

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/organizations")
class OrganizationController(
    private val organizationService: OrganizationService,
) {

    @PostMapping
    fun create(@RequestBody request: CreateOrganizationRequest): Organization = organizationService.create(request)

    @GetMapping("/{organizationId}")
    fun get(@PathVariable organizationId: UUID): Organization = organizationService.get(organizationId)

    @GetMapping
    fun list(): List<Organization> = organizationService.list()

    // Hard delete, cascades to every device/policy/command/enrollment token
    // under this org - see OrganizationService.delete for exactly what that
    // means and the one thing it deliberately refuses to cascade through.
    @DeleteMapping("/{organizationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable organizationId: UUID) = organizationService.delete(organizationId)
}
