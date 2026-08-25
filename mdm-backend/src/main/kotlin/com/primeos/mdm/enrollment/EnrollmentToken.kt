package com.primeos.mdm.enrollment

import com.primeos.mdm.admin.AdminUser
import com.primeos.mdm.device.Device
import com.primeos.mdm.device.DeviceType
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
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.util.UUID

enum class EnrollmentTokenStatus { ACTIVE, EXPIRED, REVOKED, CONSUMED }

@Entity
@Table(name = "enrollment_tokens")
class EnrollmentToken(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    var organization: Organization,

    @Enumerated(EnumType.STRING)
    @Column(name = "device_type", nullable = false)
    var deviceType: DeviceType,

    @Column(name = "token_value", nullable = false, unique = true)
    var tokenValue: String,

    @Column(name = "qr_code_data")
    var qrCodeData: String? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "default_policy_id")
    var defaultPolicy: Policy? = null,

    @Column(name = "max_uses", nullable = false)
    var maxUses: Int = 1,

    @Column(name = "used_count", nullable = false)
    var usedCount: Int = 0,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: EnrollmentTokenStatus = EnrollmentTokenStatus.ACTIVE,

    @Column(name = "expires_at")
    var expiresAt: Instant? = null,

    // GMS: raw enrollmentTokens.create response. NON_GMS: whatever your
    // provisioning flow needs beyond token_value/qr_code_data.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    var metadata: String = "{}",

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "created_by")
    var createdBy: AdminUser? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "claimed_by_device_id")
    var claimedByDevice: Device? = null,

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant? = null,
)
