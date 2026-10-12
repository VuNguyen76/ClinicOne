ALTER TABLE clinic_configuration
    ADD COLUMN max_over_capacity_appointments_per_doctor_per_day integer NOT NULL DEFAULT 3;
