package com.primeos.mdm.device

import com.primeos.mdm.admin.AdminUser
import com.primeos.mdm.organization.Organization
import com.primeos.mdm.policy.Policy
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import org.hibernate.annotations.CreationTimestamp
import java.time.Instant
import java.util.UUID

// A persisted record of a GMS DPC-migration token, created purely for
// admin visibility (see DeviceMigrationService) - it plays no role in the
// migration itself, which happens on-device via DpcMigrationClient once
// the app has tokenValue.
@Entity
@Table(name = "gms_migration_tokens")
class GmsMigrationToken(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    var organization: Organization,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "device_id", nullable = false)
    var device: Device,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "policy_id", nullable = false)
    var policy: Policy,

    @Column(name = "play_device_id", nullable = false)
    var playDeviceId: String,

    @Column(name = "play_user_id", nullable = false)
    var playUserId: String,

    @Column(name = "token_value", nullable = false)
    var tokenValue: String,

    @Column(name = "google_name")
    var googleName: String? = null,

    @Column(name = "expires_at")
    var expiresAt: Instant? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "created_by")
    var createdBy: AdminUser? = null,

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant? = null,
)
