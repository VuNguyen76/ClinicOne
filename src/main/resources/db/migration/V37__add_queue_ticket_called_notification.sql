-- Store the in-app event emitted after a doctor calls a patient from the queue.
ALTER TABLE public.patient_notifications
    DROP CONSTRAINT IF EXISTS patient_notifications_type_check;

ALTER TABLE public.patient_notifications
    ADD CONSTRAINT patient_notifications_type_check CHECK (type IN (
        'MEDICAL_RECORD_SIGNED',
        'APPOINTMENT_CREATED',
        'APPOINTMENT_CANCELLED',
        'APPOINTMENT_RESCHEDULED',
        'APPOINTMENT_RESCHEDULE_REQUIRED',
        'APPOINTMENT_REMINDER_24H',
        'APPOINTMENT_REMINDER_2H',
        'APPOINTMENT_LATE_WARNING',
        'APPOINTMENT_ABSENT',
        'QUEUE_TICKET_CALLED',
        'ACCOUNT_SECURITY_LOCKED'
    ));
