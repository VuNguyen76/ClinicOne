ALTER TABLE clinic_configuration
    ADD COLUMN max_active_patient_profiles integer NOT NULL DEFAULT 10;
