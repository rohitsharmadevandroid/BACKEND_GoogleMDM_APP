-- Bridges signupUrls.create and enterprises.create. Google's callback only
-- ever appends `enterpriseToken` to the callback URL - it does NOT echo
-- back the signupUrlName the token was issued for, and enterprises.create
-- needs both. So we persist that pairing ourselves between the two calls
-- and look it up again when the callback fires.
CREATE TABLE gms_enterprise_signups (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    signup_url_name TEXT NOT NULL,
    consumed_at     TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_gms_enterprise_signups_org ON gms_enterprise_signups(organization_id);
