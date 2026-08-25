-- One shape for both provisioning flows:
--  - GMS: token_value/qr_code_data mirror what
--    enrollmentTokens.create returns from Google; `metadata` stores the
--    raw response (expirationTimestamp, allowedManagementModes, etc.)
--    so you never lose a field Google adds later.
--  - NON_GMS: you mint token_value yourself and qr_code_data encodes
--    whatever your custom provisioning flow needs (e.g. a URL your DPC's
--    QR-provisioning intent reads, or a payload for `dpm set-device-owner`
--    scripting). `metadata` holds anything else protocol-specific.
CREATE TABLE enrollment_tokens (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    device_type         TEXT NOT NULL CHECK (device_type IN ('GMS', 'NON_GMS')),
    token_value         TEXT NOT NULL UNIQUE,
    qr_code_data        TEXT,
    default_policy_id   UUID REFERENCES policies(id),
    max_uses            INTEGER NOT NULL DEFAULT 1,
    used_count          INTEGER NOT NULL DEFAULT 0,
    status              TEXT NOT NULL DEFAULT 'ACTIVE'
                            CHECK (status IN ('ACTIVE', 'EXPIRED', 'REVOKED', 'CONSUMED')),
    expires_at          TIMESTAMPTZ,
    metadata            JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_by          UUID REFERENCES admin_users(id),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_enrollment_tokens_org ON enrollment_tokens(organization_id);
