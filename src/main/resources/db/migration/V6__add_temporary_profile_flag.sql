-- Temporary reception profiles are not linked to a patient account until verified.
ALTER TABLE public.patient_profiles
    ADD COLUMN IF NOT EXISTS temporary_profile boolean NOT NULL DEFAULT false;
