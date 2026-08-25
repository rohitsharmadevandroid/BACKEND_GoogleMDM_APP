-- One table for both device types (discriminated by device_type), not two
-- parallel tables. Fields that are always present and frequently queried
-- get real typed columns; everything path-specific and long-tail lives in
-- `metadata` JSONB. This is the key "no rewrite later" decision: when the
-- Android Management API grows a new device field, or the custom DPC
-- protocol needs a new check-in attribute, it goes into `metadata` with
-- zero migrations. Only promote a metadata field to a real column once you
-- find yourself needing to index or filter on it a lot.
--
-- gms_device_resource_name and device_uid are the two "hot lookup" keys:
-- GMS webhook/Pub/Sub payloads carry the resource name, and your DPC's
-- API calls carry the device_uid you issued it. Both are unique+indexed
-- for O(1) lookup on inbound traffic.
CREATE TABLE devices (
    id                        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id           UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    device_type               TEXT NOT NULL CHECK (device_type IN ('GMS', 'NON_GMS')),
    display_name              TEXT,
    status                    TEXT NOT NULL DEFAULT 'PROVISIONING'
                                  CHECK (status IN ('PROVISIONING', 'ACTIVE', 'INACTIVE', 'WIPED', 'DELETED')),
    policy_id                 UUID REFERENCES policies(id),
    enrollment_token_id       UUID REFERENCES enrollment_tokens(id),

    gms_enterprise_id         UUID REFERENCES gms_enterprises(id),
    gms_device_resource_name  TEXT UNIQUE, -- "enterprises/{id}/devices/{deviceId}", GMS only

    device_uid                TEXT UNIQUE, -- stable id you mint for the custom DPC, NON_GMS only
    imei                      TEXT,
    serial_number             TEXT,

    model                     TEXT,
    manufacturer              TEXT,
    os_version                TEXT,
    last_seen_at              TIMESTAMPTZ,
    metadata                  JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at                TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_device_identity CHECK (
        (device_type = 'GMS' AND gms_device_resource_name IS NOT NULL)
        OR
        (device_type = 'NON_GMS' AND device_uid IS NOT NULL)
    )
);

CREATE INDEX idx_devices_org ON devices(organization_id);
CREATE INDEX idx_devices_policy ON devices(policy_id);
CREATE INDEX idx_devices_metadata ON devices USING GIN (metadata);
