-- Raw audit log of every Pub/Sub notification received from Android
-- Management API, stored BEFORE any interpretation. Unlike everything else
-- built this session, the notification JSON shape isn't part of the
-- generated Java client (verified: no model class exists for it) - it's
-- long-stable public documentation, not a javap-checked contract. Storing
-- the raw payload first means nothing is lost even if our parsing
-- assumptions turn out wrong once tested against a real enterprise.
CREATE TABLE gms_notification_events (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    notification_type TEXT,
    raw_payload       JSONB NOT NULL,
    received_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    processed_at      TIMESTAMPTZ,
    processing_error  TEXT
);

CREATE INDEX idx_gms_notification_events_received_at ON gms_notification_events(received_at);
