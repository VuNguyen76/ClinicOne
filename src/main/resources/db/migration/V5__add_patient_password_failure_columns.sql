-- Persist temporary password-lock state used by patient password authentication.
ALTER TABLE public.patient_accounts
    ADD COLUMN IF NOT EXISTS failed_password_attempts integer NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS password_failure_window_started_at timestamp(6) with time zone,
    ADD COLUMN IF NOT EXISTS locked_until timestamp(6) with time zone;
