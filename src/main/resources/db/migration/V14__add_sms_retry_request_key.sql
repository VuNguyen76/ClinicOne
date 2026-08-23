ALTER TABLE sms_deliveries
    ADD COLUMN IF NOT EXISTS last_retry_request_key VARCHAR(120);
