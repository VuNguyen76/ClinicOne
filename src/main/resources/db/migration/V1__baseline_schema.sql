-- ClinicOne schema baseline generated from the validated PostgreSQL schema.
-- It contains structure only; no application data or credentials.
-- Flyway applies this on a new database and baselines an existing non-empty
-- database before validation. Future changes must use numbered migrations.
--
--
-- PostgreSQL database dump
--

-- Dumped from database version 16.13 (Ubuntu 16.13-1.pgdg22.04+1)
-- Dumped by pg_dump version 16.4

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: access_audit_events; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.access_audit_events (
    id uuid NOT NULL,
    actor character varying(120) NOT NULL,
    event_type character varying(40) NOT NULL,
    function character varying(180) NOT NULL,
    ip_address character varying(64),
    occurred_at timestamp(6) with time zone NOT NULL,
    outcome character varying(20) NOT NULL
);


--
-- Name: appointment_holds; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.appointment_holds (
    id uuid NOT NULL,
    appointment_date date NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    doctor_name character varying(120) NOT NULL,
    doctor_staff_id uuid,
    expires_at timestamp(6) with time zone NOT NULL,
    hold_key character varying(180) NOT NULL,
    clinic_service_id uuid,
    specialty character varying(120) NOT NULL,
    start_time time(0) without time zone NOT NULL,
    patient_account_id uuid NOT NULL
);


--
-- Name: appointment_reschedule_cases; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.appointment_reschedule_cases (
    id uuid NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    new_appointment_date date,
    new_doctor_name character varying(120),
    new_doctor_staff_id uuid,
    new_start_time time(0) without time zone,
    old_appointment_date date NOT NULL,
    old_doctor_name character varying(120) NOT NULL,
    old_doctor_staff_id uuid,
    old_start_time time(0) without time zone NOT NULL,
    reason character varying(500) NOT NULL,
    resolved_at timestamp(6) with time zone,
    specialty character varying(120) NOT NULL,
    status character varying(20) NOT NULL,
    appointment_id uuid NOT NULL,
    CONSTRAINT appointment_reschedule_cases_status_check CHECK (((status)::text = ANY ((ARRAY['OPEN'::character varying, 'RESOLVED'::character varying])::text[])))
);


--
-- Name: appointments; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.appointments (
    id uuid NOT NULL,
    appointment_code character varying(24) NOT NULL,
    appointment_date date NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    doctor_name character varying(120) NOT NULL,
    reason character varying(500) NOT NULL,
    specialty character varying(120) NOT NULL,
    start_time time(0) without time zone NOT NULL,
    status character varying(20) NOT NULL,
    patient_account_id uuid NOT NULL,
    cancellation_reason character varying(500),
    cancelled_at timestamp(6) with time zone,
    patient_profile_id uuid,
    doctor_staff_id uuid,
    cancellation_request_key character varying(80),
    checkin_request_key character varying(80),
    creation_request_key character varying(80),
    requires_medical_record boolean,
    service_duration_minutes integer,
    clinic_service_id uuid,
    service_name character varying(120),
    visit_type character varying(60),
    CONSTRAINT appointments_status_check CHECK (((status)::text = ANY ((ARRAY['BOOKED'::character varying, 'CHECKED_IN'::character varying, 'CANCELLED'::character varying, 'ABSENT'::character varying, 'COMPLETED'::character varying, 'NOT_PERFORMED'::character varying])::text[])))
);


--
-- Name: business_logs; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.business_logs (
    id uuid NOT NULL,
    actor character varying(120) NOT NULL,
    entity_id uuid NOT NULL,
    entity_type character varying(40) NOT NULL,
    event_id uuid NOT NULL,
    event_type character varying(80) NOT NULL,
    next_status character varying(40) NOT NULL,
    occurred_at timestamp(6) with time zone NOT NULL,
    previous_status character varying(40),
    reason character varying(500)
);


--
-- Name: clinic_configuration; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.clinic_configuration (
    id uuid NOT NULL,
    cancellation_threshold_hours integer NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    department_name character varying(160) NOT NULL,
    hold_minutes integer NOT NULL,
    unit_name character varying(160) NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    updated_by character varying(120) NOT NULL
);


--
-- Name: clinic_rooms; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.clinic_rooms (
    id uuid NOT NULL,
    active boolean NOT NULL,
    code character varying(32) NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    name character varying(120) NOT NULL,
    specialty character varying(120) NOT NULL,
    qr_token character varying(64)
);


--
-- Name: clinic_service_doctors; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.clinic_service_doctors (
    service_id uuid NOT NULL,
    doctor_profile_id uuid NOT NULL
);


--
-- Name: clinic_services; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.clinic_services (
    id uuid NOT NULL,
    active boolean NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    duration_minutes integer NOT NULL,
    name character varying(120) NOT NULL,
    requires_medical_record boolean,
    specialty character varying(120) NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    visit_type character varying(60) NOT NULL
);


--
-- Name: diagnosis_catalog; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.diagnosis_catalog (
    id uuid NOT NULL,
    active boolean NOT NULL,
    code character varying(50) NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    name character varying(200) NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL
);


--
-- Name: doctor_profiles; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.doctor_profiles (
    id uuid NOT NULL,
    active boolean NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    specialty character varying(120) NOT NULL,
    room_id uuid NOT NULL,
    staff_account_id uuid NOT NULL
);


--
-- Name: doctor_schedules; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.doctor_schedules (
    id uuid NOT NULL,
    active boolean NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    day_of_week character varying(10) NOT NULL,
    end_time time(0) without time zone NOT NULL,
    slot_duration_minutes integer NOT NULL,
    start_time time(0) without time zone NOT NULL,
    doctor_profile_id uuid NOT NULL,
    CONSTRAINT doctor_schedules_day_of_week_check CHECK (((day_of_week)::text = ANY ((ARRAY['MONDAY'::character varying, 'TUESDAY'::character varying, 'WEDNESDAY'::character varying, 'THURSDAY'::character varying, 'FRIDAY'::character varying, 'SATURDAY'::character varying, 'SUNDAY'::character varying])::text[])))
);


--
-- Name: doctor_time_off; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.doctor_time_off (
    id uuid NOT NULL,
    active boolean NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    end_date date NOT NULL,
    reason character varying(500) NOT NULL,
    start_date date NOT NULL,
    doctor_profile_id uuid NOT NULL
);


--
-- Name: examination_sessions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.examination_sessions (
    id uuid NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    status character varying(20) NOT NULL,
    appointment_id uuid NOT NULL,
    ended_at timestamp(6) with time zone,
    started_at timestamp(6) with time zone,
    start_request_key character varying(80),
    CONSTRAINT examination_sessions_status_check CHECK (((status)::text = ANY ((ARRAY['SCHEDULED'::character varying, 'CHECKED_IN'::character varying, 'IN_PROGRESS'::character varying, 'COMPLETED'::character varying, 'ABSENT'::character varying, 'CANCELLED'::character varying])::text[])))
);


--
-- Name: generated_clinic_slots; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.generated_clinic_slots (
    id uuid NOT NULL,
    appointment_date date NOT NULL,
    clinic_service_id uuid NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    doctor_name character varying(160) NOT NULL,
    doctor_staff_id uuid NOT NULL,
    duration_minutes integer NOT NULL,
    end_time time(0) without time zone NOT NULL,
    room_code character varying(32) NOT NULL,
    room_id uuid NOT NULL,
    specialty character varying(120) NOT NULL,
    start_time time(0) without time zone NOT NULL,
    status character varying(16) NOT NULL,
    visit_type character varying(60) NOT NULL,
    template_id uuid NOT NULL,
    CONSTRAINT generated_clinic_slots_status_check CHECK (((status)::text = ANY ((ARRAY['OPEN'::character varying, 'CANCELLED'::character varying])::text[])))
);


--
-- Name: login_sessions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.login_sessions (
    id uuid NOT NULL,
    account_id uuid NOT NULL,
    expires_at timestamp(6) with time zone NOT NULL,
    issued_at timestamp(6) with time zone NOT NULL,
    revoked_at timestamp(6) with time zone,
    token_hash character varying(64) NOT NULL,
    role character varying(200)
);


--
-- Name: medical_records; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.medical_records (
    id uuid NOT NULL,
    conclusion character varying(2000),
    diagnosis character varying(2000),
    doctor_name character varying(120),
    examination_notes character varying(2000),
    follow_up_date date,
    prescription character varying(4000),
    reason character varying(2000),
    signed_at timestamp(6) with time zone,
    treatment_plan character varying(2000),
    examination_session_id uuid NOT NULL,
    draft_saved_at timestamp(6) with time zone,
    follow_up_days integer,
    follow_up_note character varying(500),
    version bigint NOT NULL
);


--
-- Name: medication_catalog; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.medication_catalog (
    id uuid NOT NULL,
    active boolean NOT NULL,
    code character varying(50) NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    name character varying(200) NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL
);


--
-- Name: otp_challenges; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.otp_challenges (
    id uuid NOT NULL,
    code_hash character varying(100) NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    destination character varying(320) NOT NULL,
    expires_at timestamp(6) with time zone NOT NULL,
    failed_attempts integer NOT NULL,
    purpose character varying(20) NOT NULL,
    verified_at timestamp(6) with time zone,
    CONSTRAINT otp_challenges_purpose_check CHECK (((purpose)::text = ANY ((ARRAY['REGISTRATION'::character varying, 'LOGIN'::character varying, 'RECOVERY'::character varying])::text[])))
);


--
-- Name: patient_accounts; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.patient_accounts (
    id uuid NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    full_name character varying(200) NOT NULL,
    must_change_password boolean NOT NULL,
    password_hash character varying(100) NOT NULL,
    phone character varying(10) NOT NULL,
    status character varying(20) NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    address character varying(500),
    date_of_birth date,
    gender character varying(20),
    district_code character varying(10),
    district_name character varying(120),
    ethnicity character varying(100),
    identity_number character varying(12),
    nationality character varying(100),
    province_code character varying(10),
    province_name character varying(120),
    street_address character varying(500),
    ward_code character varying(10),
    ward_name character varying(120),
    CONSTRAINT patient_accounts_status_check CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'LOCKED'::character varying])::text[])))
);


--
-- Name: patient_notifications; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.patient_notifications (
    id uuid NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    event_key character varying(120) NOT NULL,
    message character varying(500) NOT NULL,
    patient_account_id uuid NOT NULL,
    read_at timestamp(6) with time zone,
    target_url character varying(300) NOT NULL,
    title character varying(160) NOT NULL,
    type character varying(40) NOT NULL,
    CONSTRAINT patient_notifications_type_check CHECK (((type)::text = ANY ((ARRAY['MEDICAL_RECORD_SIGNED'::character varying, 'APPOINTMENT_CREATED'::character varying, 'APPOINTMENT_CANCELLED'::character varying, 'APPOINTMENT_RESCHEDULED'::character varying])::text[])))
);


--
-- Name: patient_profiles; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.patient_profiles (
    id uuid NOT NULL,
    active boolean NOT NULL,
    address character varying(500),
    created_at timestamp(6) with time zone NOT NULL,
    date_of_birth date NOT NULL,
    ethnicity character varying(100),
    full_name character varying(100) NOT NULL,
    gender character varying(20) NOT NULL,
    identity_number character varying(12),
    nationality character varying(100),
    phone character varying(10),
    primary_profile boolean NOT NULL,
    relationship character varying(50) NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    owner_account_id uuid NOT NULL,
    district_code character varying(10),
    district_name character varying(120),
    province_code character varying(10),
    province_name character varying(120),
    street_address character varying(500),
    ward_code character varying(10),
    ward_name character varying(120)
);


--
-- Name: prescription_lines; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.prescription_lines (
    id uuid NOT NULL,
    dosage character varying(100) NOT NULL,
    instructions character varying(500) NOT NULL,
    line_number integer NOT NULL,
    medication_name character varying(200) NOT NULL,
    quantity integer NOT NULL,
    source_medication_id uuid,
    medical_record_id uuid NOT NULL
);


--
-- Name: queue_tickets; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.queue_tickets (
    id uuid NOT NULL,
    called_at timestamp(6) with time zone,
    checked_in_at timestamp(6) with time zone NOT NULL,
    completed_at timestamp(6) with time zone,
    queue_date date NOT NULL,
    queue_number integer NOT NULL,
    skip_reason character varying(250),
    status character varying(20) NOT NULL,
    appointment_id uuid NOT NULL,
    room_id uuid NOT NULL,
    call_count integer,
    exception_reason character varying(250),
    presence_status character varying(24),
    priority_flag boolean,
    returned_at timestamp(6) with time zone,
    routing_doctor_name character varying(120),
    routing_doctor_staff_id uuid,
    routing_specialty character varying(120),
    CONSTRAINT queue_tickets_presence_status_check CHECK (((presence_status)::text = ANY ((ARRAY['READY'::character varying, 'RETURN_REQUIRED'::character varying])::text[]))),
    CONSTRAINT queue_tickets_status_check CHECK (((status)::text = ANY ((ARRAY['WAITING'::character varying, 'CALLED'::character varying, 'IN_SERVICE'::character varying, 'SKIPPED'::character varying, 'COMPLETED'::character varying])::text[])))
);


--
-- Name: reason_catalog; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.reason_catalog (
    id uuid NOT NULL,
    active boolean NOT NULL,
    code character varying(50) NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    label character varying(160) NOT NULL,
    reason_type character varying(40) NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    CONSTRAINT reason_catalog_reason_type_check CHECK (((reason_type)::text = ANY ((ARRAY['APPOINTMENT_CANCELLATION'::character varying, 'RECEPTION_EXCEPTION'::character varying, 'QUEUE_EXCEPTION'::character varying, 'RECONCILIATION'::character varying])::text[])))
);


--
-- Name: reconciliation_incidents; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.reconciliation_incidents (
    id uuid NOT NULL,
    assignee character varying(120) NOT NULL,
    closed_at timestamp(6) with time zone,
    closed_by character varying(120),
    created_at timestamp(6) with time zone NOT NULL,
    entity_id uuid NOT NULL,
    entity_type character varying(40) NOT NULL,
    event_id uuid,
    incident_code character varying(40) NOT NULL,
    reason character varying(500) NOT NULL,
    reference_type character varying(20),
    reference_value character varying(120),
    resolution_action character varying(40),
    result_note character varying(500),
    status character varying(20) NOT NULL,
    CONSTRAINT reconciliation_incidents_reference_type_check CHECK (((reference_type)::text = ANY ((ARRAY['BUSINESS_LOG'::character varying, 'INCIDENT'::character varying])::text[]))),
    CONSTRAINT reconciliation_incidents_resolution_action_check CHECK (((resolution_action)::text = ANY ((ARRAY['RETRY_BUSINESS_ACTION'::character varying, 'REPLAY_LOG'::character varying, 'TECHNICAL_REPAIR'::character varying, 'NO_ACTION_REQUIRED'::character varying])::text[]))),
    CONSTRAINT reconciliation_incidents_status_check CHECK (((status)::text = ANY ((ARRAY['OPEN'::character varying, 'CLOSED'::character varying])::text[])))
);


--
-- Name: sms_deliveries; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.sms_deliveries (
    id uuid NOT NULL,
    attempts integer NOT NULL,
    available_at timestamp(6) with time zone NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    event_key character varying(160) NOT NULL,
    last_error character varying(500),
    locked_until timestamp(6) with time zone,
    message character varying(500) NOT NULL,
    patient_account_id uuid NOT NULL,
    phone character varying(20) NOT NULL,
    sent_at timestamp(6) with time zone,
    status character varying(20) NOT NULL,
    CONSTRAINT sms_deliveries_status_check CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'PROCESSING'::character varying, 'RETRY_WAITING'::character varying, 'SENT'::character varying, 'FAILED'::character varying])::text[])))
);


--
-- Name: staff_account_roles; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.staff_account_roles (
    staff_account_id uuid NOT NULL,
    role character varying(20) NOT NULL,
    CONSTRAINT staff_account_roles_role_check CHECK (((role)::text = ANY ((ARRAY['ADMIN'::character varying, 'COORDINATOR'::character varying, 'RECEPTIONIST'::character varying, 'DOCTOR'::character varying])::text[])))
);


--
-- Name: staff_accounts; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.staff_accounts (
    id uuid NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    full_name character varying(200) NOT NULL,
    password_hash character varying(100) NOT NULL,
    role character varying(20) NOT NULL,
    status character varying(20) NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    username character varying(80) NOT NULL,
    department_name character varying(160),
    employee_code character varying(20),
    unit_name character varying(160),
    CONSTRAINT staff_accounts_role_check CHECK (((role)::text = ANY ((ARRAY['ADMIN'::character varying, 'COORDINATOR'::character varying, 'RECEPTIONIST'::character varying, 'DOCTOR'::character varying])::text[]))),
    CONSTRAINT staff_accounts_status_check CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'LOCKED'::character varying])::text[])))
);


--
-- Name: work_schedule_template_breaks; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.work_schedule_template_breaks (
    template_id uuid NOT NULL,
    break_end time(0) without time zone NOT NULL,
    break_start time(0) without time zone NOT NULL,
    break_order integer NOT NULL,
    CONSTRAINT work_schedule_template_breaks_break_order_check CHECK ((break_order >= 0))
);


--
-- Name: work_schedule_template_exceptions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.work_schedule_template_exceptions (
    template_id uuid NOT NULL,
    exception_date date NOT NULL
);


--
-- Name: work_schedule_template_weekdays; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.work_schedule_template_weekdays (
    template_id uuid NOT NULL,
    day_of_week character varying(10) NOT NULL,
    CONSTRAINT work_schedule_template_weekdays_day_of_week_check CHECK (((day_of_week)::text = ANY ((ARRAY['MONDAY'::character varying, 'TUESDAY'::character varying, 'WEDNESDAY'::character varying, 'THURSDAY'::character varying, 'FRIDAY'::character varying, 'SATURDAY'::character varying, 'SUNDAY'::character varying])::text[])))
);


--
-- Name: work_schedule_templates; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.work_schedule_templates (
    id uuid NOT NULL,
    active boolean NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    day_end time(0) without time zone NOT NULL,
    day_start time(0) without time zone NOT NULL,
    duration_minutes integer NOT NULL,
    end_date date NOT NULL,
    specialty character varying(120) NOT NULL,
    start_date date NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    visit_type character varying(60) NOT NULL,
    clinic_service_id uuid NOT NULL,
    doctor_profile_id uuid NOT NULL,
    room_id uuid NOT NULL
);


--
-- Name: access_audit_events access_audit_events_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.access_audit_events
    ADD CONSTRAINT access_audit_events_pkey PRIMARY KEY (id);


--
-- Name: appointment_holds appointment_holds_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.appointment_holds
    ADD CONSTRAINT appointment_holds_pkey PRIMARY KEY (id);


--
-- Name: appointment_reschedule_cases appointment_reschedule_cases_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.appointment_reschedule_cases
    ADD CONSTRAINT appointment_reschedule_cases_pkey PRIMARY KEY (id);


--
-- Name: appointments appointments_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.appointments
    ADD CONSTRAINT appointments_pkey PRIMARY KEY (id);


--
-- Name: business_logs business_logs_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.business_logs
    ADD CONSTRAINT business_logs_pkey PRIMARY KEY (id);


--
-- Name: clinic_configuration clinic_configuration_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.clinic_configuration
    ADD CONSTRAINT clinic_configuration_pkey PRIMARY KEY (id);


--
-- Name: clinic_rooms clinic_rooms_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.clinic_rooms
    ADD CONSTRAINT clinic_rooms_pkey PRIMARY KEY (id);


--
-- Name: clinic_service_doctors clinic_service_doctors_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.clinic_service_doctors
    ADD CONSTRAINT clinic_service_doctors_pkey PRIMARY KEY (service_id, doctor_profile_id);


--
-- Name: clinic_services clinic_services_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.clinic_services
    ADD CONSTRAINT clinic_services_pkey PRIMARY KEY (id);


--
-- Name: diagnosis_catalog diagnosis_catalog_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.diagnosis_catalog
    ADD CONSTRAINT diagnosis_catalog_pkey PRIMARY KEY (id);


--
-- Name: doctor_profiles doctor_profiles_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.doctor_profiles
    ADD CONSTRAINT doctor_profiles_pkey PRIMARY KEY (id);


--
-- Name: doctor_schedules doctor_schedules_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.doctor_schedules
    ADD CONSTRAINT doctor_schedules_pkey PRIMARY KEY (id);


--
-- Name: doctor_time_off doctor_time_off_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.doctor_time_off
    ADD CONSTRAINT doctor_time_off_pkey PRIMARY KEY (id);


--
-- Name: examination_sessions examination_sessions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.examination_sessions
    ADD CONSTRAINT examination_sessions_pkey PRIMARY KEY (id);


--
-- Name: generated_clinic_slots generated_clinic_slots_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.generated_clinic_slots
    ADD CONSTRAINT generated_clinic_slots_pkey PRIMARY KEY (id);


--
-- Name: login_sessions login_sessions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.login_sessions
    ADD CONSTRAINT login_sessions_pkey PRIMARY KEY (id);


--
-- Name: medical_records medical_records_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.medical_records
    ADD CONSTRAINT medical_records_pkey PRIMARY KEY (id);


--
-- Name: medication_catalog medication_catalog_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.medication_catalog
    ADD CONSTRAINT medication_catalog_pkey PRIMARY KEY (id);


--
-- Name: otp_challenges otp_challenges_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.otp_challenges
    ADD CONSTRAINT otp_challenges_pkey PRIMARY KEY (id);


--
-- Name: patient_accounts patient_accounts_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.patient_accounts
    ADD CONSTRAINT patient_accounts_pkey PRIMARY KEY (id);


--
-- Name: patient_notifications patient_notifications_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.patient_notifications
    ADD CONSTRAINT patient_notifications_pkey PRIMARY KEY (id);


--
-- Name: patient_profiles patient_profiles_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.patient_profiles
    ADD CONSTRAINT patient_profiles_pkey PRIMARY KEY (id);


--
-- Name: prescription_lines prescription_lines_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.prescription_lines
    ADD CONSTRAINT prescription_lines_pkey PRIMARY KEY (id);


--
-- Name: queue_tickets queue_tickets_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.queue_tickets
    ADD CONSTRAINT queue_tickets_pkey PRIMARY KEY (id);


--
-- Name: reason_catalog reason_catalog_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reason_catalog
    ADD CONSTRAINT reason_catalog_pkey PRIMARY KEY (id);


--
-- Name: reconciliation_incidents reconciliation_incidents_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reconciliation_incidents
    ADD CONSTRAINT reconciliation_incidents_pkey PRIMARY KEY (id);


--
-- Name: sms_deliveries sms_deliveries_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sms_deliveries
    ADD CONSTRAINT sms_deliveries_pkey PRIMARY KEY (id);


--
-- Name: staff_account_roles staff_account_roles_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.staff_account_roles
    ADD CONSTRAINT staff_account_roles_pkey PRIMARY KEY (staff_account_id, role);


--
-- Name: staff_accounts staff_accounts_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.staff_accounts
    ADD CONSTRAINT staff_accounts_pkey PRIMARY KEY (id);


--
-- Name: staff_accounts uk223p20e585k33364o13y0l1nx; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.staff_accounts
    ADD CONSTRAINT uk223p20e585k33364o13y0l1nx UNIQUE (employee_code);


--
-- Name: login_sessions uk4ycpev2k8uoqapl9qk16opxe6; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.login_sessions
    ADD CONSTRAINT uk4ycpev2k8uoqapl9qk16opxe6 UNIQUE (token_hash);


--
-- Name: staff_accounts uk6fb7onep8w4asmh3x7a8dufko; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.staff_accounts
    ADD CONSTRAINT uk6fb7onep8w4asmh3x7a8dufko UNIQUE (username);


--
-- Name: appointments uk88w59a3uq8pvoxypr2ejldy0h; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.appointments
    ADD CONSTRAINT uk88w59a3uq8pvoxypr2ejldy0h UNIQUE (appointment_code);


--
-- Name: appointment_holds uk_appointment_holds_claim; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.appointment_holds
    ADD CONSTRAINT uk_appointment_holds_claim UNIQUE (hold_key);


--
-- Name: appointments uk_appointments_patient_checkin_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.appointments
    ADD CONSTRAINT uk_appointments_patient_checkin_key UNIQUE (patient_account_id, checkin_request_key);


--
-- Name: appointments uk_appointments_patient_creation_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.appointments
    ADD CONSTRAINT uk_appointments_patient_creation_key UNIQUE (patient_account_id, creation_request_key);


--
-- Name: appointments uk_appointments_patient_slot; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.appointments
    ADD CONSTRAINT uk_appointments_patient_slot UNIQUE (patient_account_id, appointment_date, start_time);


--
-- Name: business_logs uk_business_logs_event_entity; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.business_logs
    ADD CONSTRAINT uk_business_logs_event_entity UNIQUE (event_id, entity_type, entity_id);


--
-- Name: clinic_services uk_clinic_service_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.clinic_services
    ADD CONSTRAINT uk_clinic_service_key UNIQUE (name, specialty, visit_type);


--
-- Name: diagnosis_catalog uk_diagnosis_catalog_code; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.diagnosis_catalog
    ADD CONSTRAINT uk_diagnosis_catalog_code UNIQUE (code);


--
-- Name: doctor_profiles uk_doctor_profiles_staff; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.doctor_profiles
    ADD CONSTRAINT uk_doctor_profiles_staff UNIQUE (staff_account_id);


--
-- Name: examination_sessions uk_examination_sessions_appointment; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.examination_sessions
    ADD CONSTRAINT uk_examination_sessions_appointment UNIQUE (appointment_id);


--
-- Name: examination_sessions uk_examination_sessions_start_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.examination_sessions
    ADD CONSTRAINT uk_examination_sessions_start_key UNIQUE (start_request_key);


--
-- Name: generated_clinic_slots uk_generated_slot_template_time; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.generated_clinic_slots
    ADD CONSTRAINT uk_generated_slot_template_time UNIQUE (template_id, appointment_date, start_time);


--
-- Name: medical_records uk_medical_records_session; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.medical_records
    ADD CONSTRAINT uk_medical_records_session UNIQUE (examination_session_id);


--
-- Name: medication_catalog uk_medication_catalog_code; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.medication_catalog
    ADD CONSTRAINT uk_medication_catalog_code UNIQUE (code);


--
-- Name: patient_notifications uk_patient_notifications_event; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.patient_notifications
    ADD CONSTRAINT uk_patient_notifications_event UNIQUE (event_key);


--
-- Name: prescription_lines uk_prescription_lines_record_order; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.prescription_lines
    ADD CONSTRAINT uk_prescription_lines_record_order UNIQUE (medical_record_id, line_number);


--
-- Name: queue_tickets uk_queue_tickets_appointment; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.queue_tickets
    ADD CONSTRAINT uk_queue_tickets_appointment UNIQUE (appointment_id);


--
-- Name: queue_tickets uk_queue_tickets_room_date_number; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.queue_tickets
    ADD CONSTRAINT uk_queue_tickets_room_date_number UNIQUE (room_id, queue_date, queue_number);


--
-- Name: reason_catalog uk_reason_catalog_type_code; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reason_catalog
    ADD CONSTRAINT uk_reason_catalog_type_code UNIQUE (reason_type, code);


--
-- Name: reconciliation_incidents uk_reconciliation_incident_code; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reconciliation_incidents
    ADD CONSTRAINT uk_reconciliation_incident_code UNIQUE (incident_code);


--
-- Name: sms_deliveries uk_sms_deliveries_event; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sms_deliveries
    ADD CONSTRAINT uk_sms_deliveries_event UNIQUE (event_key);


--
-- Name: clinic_rooms ukgaq4qb5w4ycmjb9k6alsq65k2; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.clinic_rooms
    ADD CONSTRAINT ukgaq4qb5w4ycmjb9k6alsq65k2 UNIQUE (qr_token);


--
-- Name: clinic_rooms ukpp7nnwajl299wwfxu161advbt; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.clinic_rooms
    ADD CONSTRAINT ukpp7nnwajl299wwfxu161advbt UNIQUE (code);


--
-- Name: patient_accounts ukskqmuaknd47oignd9mqigw85q; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.patient_accounts
    ADD CONSTRAINT ukskqmuaknd47oignd9mqigw85q UNIQUE (phone);


--
-- Name: work_schedule_template_breaks work_schedule_template_breaks_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.work_schedule_template_breaks
    ADD CONSTRAINT work_schedule_template_breaks_pkey PRIMARY KEY (template_id, break_order);


--
-- Name: work_schedule_template_exceptions work_schedule_template_exceptions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.work_schedule_template_exceptions
    ADD CONSTRAINT work_schedule_template_exceptions_pkey PRIMARY KEY (template_id, exception_date);


--
-- Name: work_schedule_template_weekdays work_schedule_template_weekdays_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.work_schedule_template_weekdays
    ADD CONSTRAINT work_schedule_template_weekdays_pkey PRIMARY KEY (template_id, day_of_week);


--
-- Name: work_schedule_templates work_schedule_templates_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.work_schedule_templates
    ADD CONSTRAINT work_schedule_templates_pkey PRIMARY KEY (id);


--
-- Name: idx_appointment_holds_expiry; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_appointment_holds_expiry ON public.appointment_holds USING btree (expires_at);


--
-- Name: idx_appointment_holds_slot; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_appointment_holds_slot ON public.appointment_holds USING btree (specialty, appointment_date, start_time, doctor_staff_id);


--
-- Name: idx_appointments_doctor_slot; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_appointments_doctor_slot ON public.appointments USING btree (doctor_staff_id, appointment_date, start_time, status);


--
-- Name: idx_appointments_patient_date; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_appointments_patient_date ON public.appointments USING btree (patient_account_id, appointment_date, start_time);


--
-- Name: idx_appointments_slot_availability; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_appointments_slot_availability ON public.appointments USING btree (specialty, appointment_date, start_time, status);


--
-- Name: idx_business_logs_entity; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_business_logs_entity ON public.business_logs USING btree (entity_type, entity_id, occurred_at);


--
-- Name: idx_business_logs_event; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_business_logs_event ON public.business_logs USING btree (event_id);


--
-- Name: idx_otp_destination_purpose_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_otp_destination_purpose_created ON public.otp_challenges USING btree (destination, purpose, created_at);


--
-- Name: idx_queue_tickets_room_date_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_queue_tickets_room_date_status ON public.queue_tickets USING btree (room_id, queue_date, status);


--
-- Name: idx_reconciliation_entity; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_reconciliation_entity ON public.reconciliation_incidents USING btree (entity_type, entity_id, status);


--
-- Name: idx_reconciliation_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_reconciliation_status ON public.reconciliation_incidents USING btree (status, created_at);


--
-- Name: idx_reschedule_cases_appointment; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_reschedule_cases_appointment ON public.appointment_reschedule_cases USING btree (appointment_id, status);


--
-- Name: idx_reschedule_cases_status_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_reschedule_cases_status_created ON public.appointment_reschedule_cases USING btree (status, created_at);


--
-- Name: idx_sms_deliveries_due; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_sms_deliveries_due ON public.sms_deliveries USING btree (status, available_at);


--
-- Name: idx_sms_deliveries_patient; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_sms_deliveries_patient ON public.sms_deliveries USING btree (patient_account_id, created_at);


--
-- Name: clinic_service_doctors fk1pnk3t3fo7fuebubjw7lufqk8; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.clinic_service_doctors
    ADD CONSTRAINT fk1pnk3t3fo7fuebubjw7lufqk8 FOREIGN KEY (service_id) REFERENCES public.clinic_services(id);


--
-- Name: queue_tickets fk34gfg4i59ydop4fvmaqyiodop; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.queue_tickets
    ADD CONSTRAINT fk34gfg4i59ydop4fvmaqyiodop FOREIGN KEY (appointment_id) REFERENCES public.appointments(id);


--
-- Name: work_schedule_template_breaks fk446h79qxa9tl3rivg2p5fl80x; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.work_schedule_template_breaks
    ADD CONSTRAINT fk446h79qxa9tl3rivg2p5fl80x FOREIGN KEY (template_id) REFERENCES public.work_schedule_templates(id);


--
-- Name: staff_account_roles fk5v4cfdtr0b6c8yp590anowcno; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.staff_account_roles
    ADD CONSTRAINT fk5v4cfdtr0b6c8yp590anowcno FOREIGN KEY (staff_account_id) REFERENCES public.staff_accounts(id);


--
-- Name: appointment_holds fk61qm2by4tgrnywh1q9ql11uoj; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.appointment_holds
    ADD CONSTRAINT fk61qm2by4tgrnywh1q9ql11uoj FOREIGN KEY (patient_account_id) REFERENCES public.patient_accounts(id);


--
-- Name: work_schedule_templates fk81igvb600tdrbc4y5wagslsor; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.work_schedule_templates
    ADD CONSTRAINT fk81igvb600tdrbc4y5wagslsor FOREIGN KEY (clinic_service_id) REFERENCES public.clinic_services(id);


--
-- Name: doctor_profiles fka2fusdkvub7cr81x1hrc70v8u; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.doctor_profiles
    ADD CONSTRAINT fka2fusdkvub7cr81x1hrc70v8u FOREIGN KEY (staff_account_id) REFERENCES public.staff_accounts(id);


--
-- Name: medical_records fkay28ym8cylm7sr8rc9edqupra; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.medical_records
    ADD CONSTRAINT fkay28ym8cylm7sr8rc9edqupra FOREIGN KEY (examination_session_id) REFERENCES public.examination_sessions(id);


--
-- Name: clinic_service_doctors fkbdcvt1fmg7cdk0qy2o1nuvljj; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.clinic_service_doctors
    ADD CONSTRAINT fkbdcvt1fmg7cdk0qy2o1nuvljj FOREIGN KEY (doctor_profile_id) REFERENCES public.doctor_profiles(id);


--
-- Name: patient_profiles fkc8li41hi9th4e47n075fqty5s; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.patient_profiles
    ADD CONSTRAINT fkc8li41hi9th4e47n075fqty5s FOREIGN KEY (owner_account_id) REFERENCES public.patient_accounts(id);


--
-- Name: prescription_lines fkgkc6h2bc1nmif5a6ic0yhrlcf; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.prescription_lines
    ADD CONSTRAINT fkgkc6h2bc1nmif5a6ic0yhrlcf FOREIGN KEY (medical_record_id) REFERENCES public.medical_records(id);


--
-- Name: appointments fkgm77pu9m78dxalqojj67wbalt; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.appointments
    ADD CONSTRAINT fkgm77pu9m78dxalqojj67wbalt FOREIGN KEY (patient_account_id) REFERENCES public.patient_accounts(id);


--
-- Name: examination_sessions fkh88noq4nk8ddjr1nacnawu7y2; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.examination_sessions
    ADD CONSTRAINT fkh88noq4nk8ddjr1nacnawu7y2 FOREIGN KEY (appointment_id) REFERENCES public.appointments(id);


--
-- Name: doctor_schedules fkh9b5573l22uvcj9l13na8baqv; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.doctor_schedules
    ADD CONSTRAINT fkh9b5573l22uvcj9l13na8baqv FOREIGN KEY (doctor_profile_id) REFERENCES public.doctor_profiles(id);


--
-- Name: doctor_time_off fklhj3soxbasd0ypmg8xdpnykqv; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.doctor_time_off
    ADD CONSTRAINT fklhj3soxbasd0ypmg8xdpnykqv FOREIGN KEY (doctor_profile_id) REFERENCES public.doctor_profiles(id);


--
-- Name: appointment_reschedule_cases fkmd80y9ayx93f4g29b0lgkc01o; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.appointment_reschedule_cases
    ADD CONSTRAINT fkmd80y9ayx93f4g29b0lgkc01o FOREIGN KEY (appointment_id) REFERENCES public.appointments(id);


--
-- Name: work_schedule_template_weekdays fknasq4mr4wa5n9flfrexth7g83; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.work_schedule_template_weekdays
    ADD CONSTRAINT fknasq4mr4wa5n9flfrexth7g83 FOREIGN KEY (template_id) REFERENCES public.work_schedule_templates(id);


--
-- Name: queue_tickets fknujvjnoeajmir4xaisfxwvw41; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.queue_tickets
    ADD CONSTRAINT fknujvjnoeajmir4xaisfxwvw41 FOREIGN KEY (room_id) REFERENCES public.clinic_rooms(id);


--
-- Name: generated_clinic_slots fko06r7tvd1ym8ppl119q8cya84; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.generated_clinic_slots
    ADD CONSTRAINT fko06r7tvd1ym8ppl119q8cya84 FOREIGN KEY (template_id) REFERENCES public.work_schedule_templates(id);


--
-- Name: work_schedule_template_exceptions fkp75k1vxfwi9f4hlw039qlocbf; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.work_schedule_template_exceptions
    ADD CONSTRAINT fkp75k1vxfwi9f4hlw039qlocbf FOREIGN KEY (template_id) REFERENCES public.work_schedule_templates(id);


--
-- Name: doctor_profiles fkq294gbt6ldaodp1qux24xvm8a; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.doctor_profiles
    ADD CONSTRAINT fkq294gbt6ldaodp1qux24xvm8a FOREIGN KEY (room_id) REFERENCES public.clinic_rooms(id);


--
-- Name: appointments fkqx253hq86d4bk773wiiye6jic; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.appointments
    ADD CONSTRAINT fkqx253hq86d4bk773wiiye6jic FOREIGN KEY (patient_profile_id) REFERENCES public.patient_profiles(id);


--
-- Name: work_schedule_templates fkrfau0w1iifnojbm1ovli3o069; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.work_schedule_templates
    ADD CONSTRAINT fkrfau0w1iifnojbm1ovli3o069 FOREIGN KEY (doctor_profile_id) REFERENCES public.doctor_profiles(id);


--
-- Name: work_schedule_templates fkte1nxtb9u4s32ei78pdfb7b2g; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.work_schedule_templates
    ADD CONSTRAINT fkte1nxtb9u4s32ei78pdfb7b2g FOREIGN KEY (room_id) REFERENCES public.clinic_rooms(id);


--
-- PostgreSQL database dump complete
--
