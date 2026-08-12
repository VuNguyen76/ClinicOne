-- Sealed snapshot for a doctor who started the wrong patient profile.
CREATE TABLE IF NOT EXISTS public.wrong_profile_incidents (
    id uuid NOT NULL,
    source_medical_record_id uuid,
    examination_session_id uuid NOT NULL,
    queue_ticket_id uuid NOT NULL,
    appointment_id uuid NOT NULL,
    doctor_staff_id uuid NOT NULL,
    reason character varying(500) NOT NULL,
    draft_reason character varying(2000),
    examination_notes character varying(2000),
    diagnosis character varying(2000),
    conclusion character varying(2000),
    treatment_plan character varying(2000),
    prescription_snapshot text,
    follow_up_date date,
    follow_up_days integer,
    follow_up_note character varying(500),
    started_at timestamp(6) with time zone,
    sealed_at timestamp(6) with time zone NOT NULL,
    CONSTRAINT wrong_profile_incidents_pkey PRIMARY KEY (id)
);
