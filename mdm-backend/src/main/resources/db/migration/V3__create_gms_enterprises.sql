-- One row per organization that has onboarded onto Google's Android
-- Management API. Non-GMS-only orgs simply never get a row here.
-- UNIQUE(organization_id) assumes 1 org : 1 Google enterprise; drop that
-- constraint later if you ever need an org to own multiple enterprises.
CREATE TABLE gms_enterprises (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id             UUID NOT NULL UNIQUE REFERENCES organizations(id) ON DELETE CASCADE,
    enterprise_name             TEXT NOT NULL UNIQUE, -- Google resource name, e.g. "enterprises/LC00abc123"
    gcp_project_id              TEXT NOT NULL,
    pubsub_topic                TEXT,
    -- Points at a Secret Manager entry; the raw service-account key never
    -- touches this database.
    service_account_secret_ref TEXT NOT NULL,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT now()
);
