-- Persisted record of every GMS DPC-migration token created (see
-- DeviceMigrationService). Deliberately a separate table from
-- enrollment_tokens rather than folded into it - a migration token is for
-- an already-enrolled device transitioning management systems, not a new
-- device enrolling, and several enrollment_tokens columns (max_uses,
-- used_count, claimed_by_device_id, the GMS/NON_GMS device_type CHECK)
-- don't have a meaningful equivalent here.
CREATE TABLE gms_migration_tokens (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    device_id           UUID NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
    policy_id           UUID NOT NULL REFERENCES policies(id),
    play_device_id      TEXT NOT NULL,
    play_user_id        TEXT NOT NULL,
    token_value         TEXT NOT NULL,
    google_name         TEXT,
    expires_at          TIMESTAMPTZ,
    created_by          UUID REFERENCES admin_users(id),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_gms_migration_tokens_org ON gms_migration_tokens(organization_id);
CREATE INDEX idx_gms_migration_tokens_device ON gms_migration_tokens(device_id);
