-- Clean up obsolete reschedule cases where the underlying appointment has already expired, been cancelled, or not performed
UPDATE public.appointment_reschedule_cases AS rc
SET status = 'RESOLVED',
    resolved_at = NOW(),
    reason = CONCAT(rc.reason, ' [Tự động đóng do lịch gốc đã kết thúc: ', a.status, ']')
FROM public.appointments AS a
WHERE rc.appointment_id = a.id
  AND rc.status = 'OPEN'
  AND a.status IN ('CANCELLED', 'ABSENT', 'NOT_PERFORMED', 'COMPLETED');

-- Optimize reschedule cases query by filtering on appointment status
CREATE INDEX IF NOT EXISTS idx_reschedule_cases_appointment_status
    ON public.appointment_reschedule_cases (appointment_id, status);
