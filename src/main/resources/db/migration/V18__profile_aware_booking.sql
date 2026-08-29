DO $$
DECLARE
    appointment_record RECORD;
    primary_profile_id UUID;
BEGIN
    FOR appointment_record IN
        SELECT a.id, a.patient_account_id
        FROM public.appointments a
        WHERE a.patient_profile_id IS NULL
    LOOP
        SELECT id INTO primary_profile_id
        FROM public.patient_profiles
        WHERE owner_id = appointment_record.patient_account_id
          AND active = true
          AND primary_profile = true
        LIMIT 1;

        IF primary_profile_id IS NULL THEN
            INSERT INTO public.patient_profiles (
                id, owner_id, full_name, relationship, date_of_birth, gender,
                phone, identity_number, nationality, ethnicity, address,
                province_code, province_name, district_code, district_name,
                ward_code, ward_name, street_address, active, primary_profile,
                created_at, updated_at
            )
            SELECT
                gen_random_uuid(),
                p.id,
                p.full_name,
                'Bản thân',
                p.date_of_birth,
                p.gender,
                p.phone,
                p.identity_number,
                p.nationality,
                p.ethnicity,
                p.address,
                p.province_code,
                p.province_name,
                p.district_code,
                p.district_name,
                p.ward_code,
                p.ward_name,
                p.street_address,
                true,
                true,
                CURRENT_TIMESTAMP,
                CURRENT_TIMESTAMP
            FROM public.patient_accounts p
            WHERE p.id = appointment_record.patient_account_id
            RETURNING id INTO primary_profile_id;
        END IF;

        UPDATE public.appointments
        SET patient_profile_id = primary_profile_id
        WHERE id = appointment_record.id;
    END LOOP;
END $$;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'appointments' AND column_name = 'patient_profile_id' AND is_nullable = 'YES'
    ) THEN
        ALTER TABLE public.appointments ALTER COLUMN patient_profile_id SET NOT NULL;
    END IF;
END $$;

ALTER TABLE public.appointments DROP CONSTRAINT IF EXISTS uk_appointments_patient_slot;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'uk_appointments_patient_profile_slot'
    ) THEN
        ALTER TABLE public.appointments ADD CONSTRAINT uk_appointments_patient_profile_slot
            UNIQUE (patient_profile_id, appointment_date, start_time);
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'appointment_holds' AND column_name = 'patient_profile_id'
    ) THEN
        ALTER TABLE public.appointment_holds ADD COLUMN patient_profile_id uuid;
    END IF;
END $$;

ALTER TABLE public.appointment_holds DROP CONSTRAINT IF EXISTS uk_appointment_holds_claim;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'uk_appointment_holds_claim'
    ) THEN
        ALTER TABLE public.appointment_holds ADD CONSTRAINT uk_appointment_holds_claim
            UNIQUE (hold_key);
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_indexes WHERE indexname = 'idx_appointments_profile_slot'
    ) THEN
        CREATE INDEX idx_appointments_profile_slot ON public.appointments (patient_profile_id, appointment_date, start_time);
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE constraint_name = 'fk_appointments_patient_profile'
    ) THEN
        ALTER TABLE public.appointments
            ADD CONSTRAINT fk_appointments_patient_profile
            FOREIGN KEY (patient_profile_id)
            REFERENCES public.patient_profiles(id);
    END IF;
END $$;

