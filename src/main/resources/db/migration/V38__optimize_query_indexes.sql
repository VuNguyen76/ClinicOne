-- Optimize frequent lookup and composite query paths for patient profiles, clinic slots, and queue operations

-- 1. Accelerate patient profile listings by owner and active flag
CREATE INDEX IF NOT EXISTS idx_patient_profiles_owner_active
    ON public.patient_profiles (owner_account_id, active, primary_profile DESC, created_at ASC);

-- 2. Accelerate reception search by patient phone
CREATE INDEX IF NOT EXISTS idx_patient_profiles_phone
    ON public.patient_profiles (phone)
    WHERE phone IS NOT NULL;

-- 3. Accelerate slot search by doctor, appointment date, and status
CREATE INDEX IF NOT EXISTS idx_slots_doctor_date_status
    ON public.generated_clinic_slots (doctor_staff_id, appointment_date, status, start_time ASC);

-- 4. Accelerate slot search by room, appointment date, and status
CREATE INDEX IF NOT EXISTS idx_slots_room_date_status
    ON public.generated_clinic_slots (room_id, appointment_date, status, start_time ASC);

-- 5. Accelerate slot search by service, appointment date, and status
CREATE INDEX IF NOT EXISTS idx_slots_service_date_status
    ON public.generated_clinic_slots (clinic_service_id, appointment_date, status, start_time ASC);

-- 6. Accelerate patient queue tickets lookup by queue date and status
CREATE INDEX IF NOT EXISTS idx_queue_tickets_date_status
    ON public.queue_tickets (queue_date, status, queue_number ASC);
