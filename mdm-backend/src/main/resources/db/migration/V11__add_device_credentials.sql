-- Per-device credential for the non-GMS sync protocol (checkin/ack).
-- GMS devices never populate these - they authenticate to us via nothing
-- (we call OUT to Google), whereas non-GMS devices call IN to us and need
-- something to prove which device they are.
--
-- Only the hash is stored; the raw key is shown to the DPC app exactly once
-- at enrollment and never persisted. One active credential per device for
-- now (revoke-and-replace, not rotation history) - a dedicated
-- device_credentials table would be the upgrade path if that's ever not
-- enough.
ALTER TABLE devices
    ADD COLUMN credential_hash TEXT UNIQUE,
    ADD COLUMN credential_issued_at TIMESTAMPTZ,
    ADD COLUMN credential_revoked_at TIMESTAMPTZ;

CREATE INDEX idx_devices_credential_hash ON devices(credential_hash);
