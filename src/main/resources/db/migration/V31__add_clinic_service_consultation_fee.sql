ALTER TABLE clinic_services
    ADD COLUMN IF NOT EXISTS consultation_fee BIGINT NOT NULL DEFAULT 0;

ALTER TABLE invoices
    ADD COLUMN IF NOT EXISTS invoice_type VARCHAR(30) NOT NULL DEFAULT 'PAYMENT_RECEIPT';

CREATE INDEX IF NOT EXISTS idx_invoices_appointment ON invoices(appointment_id);
