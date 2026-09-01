-- REQUEST_DEVICE_INFO (and any future info-returning command) has no way
-- to get its collected data back to the backend today - the ack endpoint
-- only carries {status, errorMessage}. This is the free-form key/value
-- store for whatever the DPC collected, set via the same ack call.
ALTER TABLE commands ADD COLUMN result_data JSONB NOT NULL DEFAULT '{}'::jsonb;
