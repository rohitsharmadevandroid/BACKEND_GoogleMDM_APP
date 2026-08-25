package com.primeos.mdm.enterprise

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "gms_notification_events")
class GmsNotificationEvent(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,

    @Column(name = "notification_type")
    var notificationType: String? = null,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_payload", nullable = false, columnDefinition = "jsonb")
    var rawPayload: String,

    @CreationTimestamp
    @Column(name = "received_at", nullable = false, updatable = false)
    var receivedAt: Instant? = null,

    @Column(name = "processed_at")
    var processedAt: Instant? = null,

    @Column(name = "processing_error")
    var processingError: String? = null,
)
