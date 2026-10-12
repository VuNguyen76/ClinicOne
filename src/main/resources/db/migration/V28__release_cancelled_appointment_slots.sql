ALTER TABLE public.appointments
    DROP CONSTRAINT IF EXISTS uk_appointments_patient_profile_slot;

CREATE UNIQUE INDEX IF NOT EXISTS uk_appointments_patient_profile_slot
    ON public.appointments (patient_profile_id, appointment_date, start_time)
    WHERE status IN ('BOOKED', 'CHECKED_IN');
