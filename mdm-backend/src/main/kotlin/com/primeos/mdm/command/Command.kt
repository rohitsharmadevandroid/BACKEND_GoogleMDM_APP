package com.primeos.mdm.command

import com.primeos.mdm.admin.AdminUser
import com.primeos.mdm.device.Device
import com.primeos.mdm.organization.Organization
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

// The DB column is plain TEXT (no CHECK constraint) on purpose - adding a
// new command type is just a new enum constant here, never a migration.
//
// Corrected against the real Android Management API (verified via javap,
// not docs recall): SET_KIOSK_MODE and INSTALL_APP/UNINSTALL_APP were wrong
// from the original schema design - those are POLICY settings
// (Policy.kioskCustomLauncherEnabled / Policy.applications), not issuable
// commands, and are already handled by the policy translator. RESET_PASSWORD
// replaces the originally-assumed CLEAR_PASSCODE to match Google's actual
// Command.type vocabulary.
enum class CommandType { LOCK, WIPE, REBOOT, RESET_PASSWORD, CLEAR_APP_DATA, REQUEST_DEVICE_INFO }

enum class CommandStatus { PENDING, SENT, ACKNOWLEDGED, COMPLETED, FAILED, EXPIRED }

@Entity
@Table(name = "commands")
class Command(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    var organization: Organization,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "device_id", nullable = false)
    var device: Device,

    @Enumerated(EnumType.STRING)
    @Column(name = "command_type", nullable = false)
    var commandType: CommandType,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: CommandStatus = CommandStatus.PENDING,

    // Internal, vendor-neutral params, e.g. WIPE -> {"preserveData": false}
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    var payload: String = "{}",

    @Column(name = "gms_command_resource_name")
    var gmsCommandResourceName: String? = null,

    @Column(name = "error_message")
    var errorMessage: String? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "issued_by")
    var issuedBy: AdminUser? = null,

    @Column(name = "dispatched_at")
    var dispatchedAt: Instant? = null,

    @Column(name = "completed_at")
    var completedAt: Instant? = null,

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant? = null,

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant? = null,
)
