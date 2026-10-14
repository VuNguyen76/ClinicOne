-- Migration V42: Add missing high-impact indexes for Doctor Examination Signing & Invoice Generation
-- 1. Accelerate finding prescription lines when signing or loading a medical record
CREATE INDEX IF NOT EXISTS idx_prescription_lines_record
    ON public.prescription_lines (medical_record_id, line_number ASC);

-- 2. Accelerate medical record lookup by session id
CREATE INDEX IF NOT EXISTS idx_medical_records_session
    ON public.medical_records (examination_session_id);

-- 3. Accelerate examination session lookup by appointment id
CREATE INDEX IF NOT EXISTS idx_examination_sessions_appointment
    ON public.examination_sessions (appointment_id, status);

-- 4. Accelerate invoice lookup by appointment and session
CREATE INDEX IF NOT EXISTS idx_invoices_session
    ON public.invoices (session_id);

CREATE INDEX IF NOT EXISTS idx_invoices_appointment_id
    ON public.invoices (appointment_id);
