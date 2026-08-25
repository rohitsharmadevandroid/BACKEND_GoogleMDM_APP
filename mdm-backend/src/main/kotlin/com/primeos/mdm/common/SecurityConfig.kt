package com.primeos.mdm.common

import com.primeos.mdm.admin.AdminAuthenticationFilter
import com.primeos.mdm.admin.JwtService
import com.primeos.mdm.device.DeviceCredentialService
import com.primeos.mdm.device.DeviceRepository
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.HttpStatusEntryPoint
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter

// Two independent auth mechanisms share this one filter chain:
//  - AdminAuthenticationFilter: JWT bearer tokens for admin-facing /api/**
//    endpoints, integrated with Spring Security's own role-based
//    authorizeHttpRequests rules below (SUPER_ADMIN / ORG_ADMIN / ORG_VIEWER).
//  - DeviceAuthenticationFilter: a separate, simpler per-device API key
//    check for /api/dpc/** (checkin/ack), which has no role concept and
//    doesn't need Spring Security's authentication machinery at all.
//
// Per-organization data isolation (WHICH org's data, as opposed to WHAT
// KIND of action) is enforced separately, in each org-scoped service via
// AdminAccessGuard - see that class. Role rules here only gate action kind.
@Configuration
class SecurityConfig {

    @Bean
    fun securityFilterChain(
        http: HttpSecurity,
        deviceRepository: DeviceRepository,
        deviceCredentialService: DeviceCredentialService,
        jwtService: JwtService,
    ): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            // Without an explicit entry point, Spring Security defaults to
            // Http403ForbiddenEntryPoint for ANY authentication failure -
            // meaning "you sent no/bad credentials" and "you're logged in
            // but not allowed" both come back as 403. This reserves 403
            // for the latter and makes the former a proper 401.
            .exceptionHandling { it.authenticationEntryPoint(HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)) }
            .authorizeHttpRequests {
                it
                    // Spring Boot's internal error dispatch forward - without
                    // this, an unhandled exception anywhere gets its /error
                    // forward blocked by anyRequest().authenticated() below,
                    // producing a blank 403 instead of the real error (this is
                    // exactly what a bad login looked like before this line
                    // was added).
                    .requestMatchers("/error").permitAll()
                    .requestMatchers("/api/auth/login").permitAll()
                    .requestMatchers("/api/dpc/**").permitAll()
                    // Google's browser redirect after enterprise sign-up hits
                    // this - it carries no admin JWT at all (the enterpriseToken
                    // in the query string is its own one-time proof), so this
                    // MUST stay public. Regression risk: it's a GET under
                    // /api/**, so without this exact carve-out it'd silently
                    // fall into the generic GET rule below and start requiring
                    // admin auth that Google's redirect can never provide.
                    .requestMatchers(HttpMethod.GET, "/api/organizations/*/gms-enterprise/callback").permitAll()
                    // create/list-all/deactivate for admin-users are all
                    // platform-level (see AdminUserService kdocs) - the
                    // per-org admin-user list is a separate, AdminAccessGuard-
                    // scoped endpoint (OrgScopedAdminUserController) that
                    // falls through to the generic authenticated() GET rule
                    // below instead.
                    .requestMatchers(HttpMethod.POST, "/api/admin-users").hasRole("SUPER_ADMIN")
                    .requestMatchers(HttpMethod.GET, "/api/admin-users").hasRole("SUPER_ADMIN")
                    .requestMatchers(HttpMethod.POST, "/api/admin-users/*/deactivate").hasRole("SUPER_ADMIN")
                    // Creating/listing organizations themselves is
                    // platform-level, not org-scoped - an org-scoped admin has
                    // no existing org to compare a new one against, and
                    // listing ALL orgs indiscriminately doesn't fit the
                    // "only your own org" model AdminAccessGuard enforces
                    // everywhere else.
                    .requestMatchers(HttpMethod.POST, "/api/organizations").hasRole("SUPER_ADMIN")
                    .requestMatchers(HttpMethod.GET, "/api/organizations").hasRole("SUPER_ADMIN")
                    .requestMatchers(HttpMethod.GET, "/api/**").authenticated()
                    .requestMatchers(HttpMethod.POST, "/api/**").hasAnyRole("SUPER_ADMIN", "ORG_ADMIN")
                    .requestMatchers(HttpMethod.PUT, "/api/**").hasAnyRole("SUPER_ADMIN", "ORG_ADMIN")
                    .requestMatchers(HttpMethod.DELETE, "/api/**").hasAnyRole("SUPER_ADMIN", "ORG_ADMIN")
                    // Every /api/** path is already covered by an explicit
                    // rule above (the 4 method-based rules catch anything
                    // not more specifically matched first) - so this
                    // catch-all only ever applies to non-API requests,
                    // which today means the dashboard's static HTML/JS/CSS.
                    // Those must be reachable before login (you need to
                    // load the login page itself), so this is intentionally
                    // permitAll, not a security regression on the API.
                    .anyRequest().permitAll()
            }
            .addFilterBefore(
                DeviceAuthenticationFilter(deviceRepository, deviceCredentialService),
                UsernamePasswordAuthenticationFilter::class.java,
            )
            .addFilterBefore(
                AdminAuthenticationFilter(jwtService),
                UsernamePasswordAuthenticationFilter::class.java,
            )
        return http.build()
    }
}
