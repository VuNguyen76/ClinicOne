ALTER TABLE appointment_holds
    ADD COLUMN IF NOT EXISTS session_key VARCHAR(120);

UPDATE appointment_holds
SET session_key = 'LEGACY'
WHERE session_key IS NULL;

ALTER TABLE appointment_holds
    ALTER COLUMN session_key SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_appointment_holds_patient_session
    ON appointment_holds(patient_account_id, session_key, expires_at);
