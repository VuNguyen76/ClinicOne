CREATE INDEX IF NOT EXISTS idx_appointments_patient_profile
    ON appointments (patient_account_id, patient_profile_id, id);

CREATE INDEX IF NOT EXISTS idx_medical_records_session_signed
    ON medical_records (examination_session_id, signed_at DESC);
