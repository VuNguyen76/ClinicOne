-- Keep the notification type constraint in sync with PatientNotificationType.
-- V1 was created before reminder, lifecycle and account-security notifications
-- were added, so those events would otherwise fail at the database boundary.
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
        'ACCOUNT_SECURITY_LOCKED'
    ));
