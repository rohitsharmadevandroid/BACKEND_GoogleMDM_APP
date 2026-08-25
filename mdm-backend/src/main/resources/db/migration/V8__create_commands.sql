-- Unified command history for both paths. `payload` is your internal,
-- vendor-neutral command params (e.g. WIPE -> {"preserveData": false}).
-- gms_command_resource_name is only set for GMS commands, so you can
-- reconcile Android Management API operation status back onto this row.
CREATE TABLE commands (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id             UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    device_id                   UUID NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
    command_type                TEXT NOT NULL, -- LOCK, WIPE, REBOOT, CLEAR_PASSCODE, SET_KIOSK_MODE, INSTALL_APP, ...
    status                      TEXT NOT NULL DEFAULT 'PENDING'
                                    CHECK (status IN ('PENDING', 'SENT', 'ACKNOWLEDGED', 'COMPLETED', 'FAILED', 'EXPIRED')),
    payload                     JSONB NOT NULL DEFAULT '{}'::jsonb,
    gms_command_resource_name   TEXT,
    error_message               TEXT,
    issued_by                   UUID REFERENCES admin_users(id),
    dispatched_at               TIMESTAMPTZ,
    completed_at                TIMESTAMPTZ,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_commands_device ON commands(device_id);
CREATE INDEX idx_commands_org ON commands(organization_id);
CREATE INDEX idx_commands_status ON commands(status);
