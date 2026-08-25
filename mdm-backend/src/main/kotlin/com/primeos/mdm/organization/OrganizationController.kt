package com.primeos.mdm.organization

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
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
}
