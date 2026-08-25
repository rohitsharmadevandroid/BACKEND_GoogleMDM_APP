package com.primeos.mdm.enterprise

import com.primeos.mdm.organization.Organization
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.OneToOne
import jakarta.persistence.Table
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "gms_enterprises")
class GmsEnterprise(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,

    // Column is UNIQUE in the DB, so this is a real 1:1 (one org : one Google
    // enterprise). Relax to @ManyToOne if an org ever needs multiple enterprises.
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false, unique = true)
    var organization: Organization,

    @Column(name = "enterprise_name", nullable = false, unique = true)
    var enterpriseName: String,

    @Column(name = "gcp_project_id", nullable = false)
    var gcpProjectId: String,

    @Column(name = "pubsub_topic")
    var pubsubTopic: String? = null,

    // Points at a Secret Manager entry - the raw service-account key never lives here.
    @Column(name = "service_account_secret_ref", nullable = false)
    var serviceAccountSecretRef: String,

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant? = null,

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant? = null,
)
