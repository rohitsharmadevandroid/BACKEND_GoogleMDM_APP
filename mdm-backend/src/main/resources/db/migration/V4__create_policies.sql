-- `definition` is YOUR vendor-neutral policy shape (app restrictions, wifi
-- config, password policy, kiosk mode, etc.) — the thing an admin actually
-- edits. It is never Google's policy JSON or the DPC's payload directly;
-- the translator layer (step #5) derives both of those FROM this.
CREATE TABLE policies (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    name            TEXT NOT NULL,
    description     TEXT,
    version         INTEGER NOT NULL DEFAULT 1,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    definition      JSONB NOT NULL,
    created_by      UUID REFERENCES admin_users(id),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (organization_id, name)
);

CREATE INDEX idx_policies_org ON policies(organization_id);
CREATE INDEX idx_policies_definition ON policies USING GIN (definition);
