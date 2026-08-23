ALTER TABLE appointments
    ADD CONSTRAINT uk_appointments_profile_creation_key
    UNIQUE (patient_profile_id, creation_request_key);
