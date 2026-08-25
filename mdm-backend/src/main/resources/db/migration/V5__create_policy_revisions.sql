-- Append-only audit trail. An MDM product needs "who changed the wifi
-- policy and when" for compliance and for rollback, so we snapshot the
-- previous `definition` here every time `policies.definition` changes
-- (do this in application code, e.g. an @PrePersist/@PreUpdate hook or an
-- explicit service-layer step — not a DB trigger, to keep the logic visible
-- in Kotlin).
CREATE TABLE policy_revisions (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    policy_id   UUID NOT NULL REFERENCES policies(id) ON DELETE CASCADE,
    version     INTEGER NOT NULL,
    definition  JSONB NOT NULL,
    change_note TEXT,
    changed_by  UUID REFERENCES admin_users(id),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (policy_id, version)
);
