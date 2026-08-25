package com.primeos.mdm.device

import com.primeos.mdm.enrollment.EnrollmentToken
import com.primeos.mdm.enterprise.GmsEnterprise
import com.primeos.mdm.organization.Organization
import com.primeos.mdm.policy.Policy
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.annotations.UpdateTimestamp
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.util.UUID

enum class DeviceStatus { PROVISIONING, ACTIVE, INACTIVE, WIPED, DELETED }

@Entity
@Table(name = "devices")
class Device(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    var organization: Organization,

    @Enumerated(EnumType.STRING)
    @Column(name = "device_type", nullable = false)
    var deviceType: DeviceType,

    @Column(name = "display_name")
    var displayName: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: DeviceStatus = DeviceStatus.PROVISIONING,

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "policy_id")
    var policy: Policy? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "enrollment_token_id")
    var enrollmentToken: EnrollmentToken? = null,

    // --- GMS-only fields (set when deviceType == GMS) ---
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "gms_enterprise_id")
    var gmsEnterprise: GmsEnterprise? = null,

    @Column(name = "gms_device_resource_name", unique = true)
    var gmsDeviceResourceName: String? = null,

    // --- NON_GMS-only fields (set when deviceType == NON_GMS) ---
    @Column(name = "device_uid", unique = true)
    var deviceUid: String? = null,

    @Column
    var imei: String? = null,

    @Column(name = "serial_number")
    var serialNumber: String? = null,

    // --- common metadata ---
    @Column
    var model: String? = null,

    @Column
    var manufacturer: String? = null,

    @Column(name = "os_version")
    var osVersion: String? = null,

    @Column(name = "last_seen_at")
    var lastSeenAt: Instant? = null,

    // --- non-GMS sync protocol credential (see V11 migration) ---
    @Column(name = "credential_hash", unique = true)
    var credentialHash: String? = null,

    @Column(name = "credential_issued_at")
    var credentialIssuedAt: Instant? = null,

    @Column(name = "credential_revoked_at")
    var credentialRevokedAt: Instant? = null,

    // Long-tail, type-specific attributes that don't yet warrant a real
    // column (see V7 migration comment for the reasoning).
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    var metadata: String = "{}",

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant? = null,

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant? = null,
)
