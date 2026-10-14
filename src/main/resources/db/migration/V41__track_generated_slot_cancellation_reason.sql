ALTER TABLE generated_clinic_slots
    ADD COLUMN cancellation_reason VARCHAR(32) NOT NULL DEFAULT 'OTHER';
