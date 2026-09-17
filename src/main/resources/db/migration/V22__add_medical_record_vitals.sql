-- V22: Add vital signs and physical indicators to medical_records
ALTER TABLE medical_records
    ADD COLUMN IF NOT EXISTS blood_pressure VARCHAR(30),
    ADD COLUMN IF NOT EXISTS heart_rate INTEGER,
    ADD COLUMN IF NOT EXISTS temperature NUMERIC(4, 1),
    ADD COLUMN IF NOT EXISTS sp_o2 INTEGER,
    ADD COLUMN IF NOT EXISTS weight NUMERIC(5, 1),
    ADD COLUMN IF NOT EXISTS height NUMERIC(5, 1),
    ADD COLUMN IF NOT EXISTS allergy_summary VARCHAR(500);
