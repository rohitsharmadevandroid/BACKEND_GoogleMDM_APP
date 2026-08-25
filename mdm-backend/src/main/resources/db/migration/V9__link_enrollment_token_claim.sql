-- enrollment_tokens -> devices is added as a separate migration (rather
-- than in V6) to avoid a circular FK dependency: devices.enrollment_token_id
-- points at enrollment_tokens, and this column points back at devices.
ALTER TABLE enrollment_tokens
    ADD COLUMN claimed_by_device_id UUID REFERENCES devices(id);

CREATE INDEX idx_enrollment_tokens_claimed_device ON enrollment_tokens(claimed_by_device_id);
