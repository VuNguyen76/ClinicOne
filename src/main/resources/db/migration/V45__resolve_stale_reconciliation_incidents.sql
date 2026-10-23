-- Migration V45: Clean up stale queue ticket and close false reconciliation incidents

-- 1. Clean up stale ticket CL-65HG9FGAQEM8 stuck in CALLED state from 10+ days ago
UPDATE queue_tickets
SET status = 'LEFT_BEFORE_EXAM',
    closure_outcome = 'LEFT_BEFORE_EXAM',
    completed_at = CURRENT_TIMESTAMP
WHERE id = 'f975f30d-a365-4bc2-aca0-b7a35aae9b78'
  AND status = 'CALLED';

UPDATE appointments
SET status = 'NOT_PERFORMED'
WHERE id = '139a6cac-41d5-4df6-a62a-c25db326fb8f'
  AND status = 'CHECKED_IN';

UPDATE examination_sessions
SET status = 'CANCELLED'
WHERE id = 'fdcc93fb-1efc-4708-9ec9-179eeefdbcd4'
  AND status = 'SCHEDULED';

-- 2. Synchronize business log journal for entities whose status transitions were missing or out of order
INSERT INTO business_logs (id, actor, entity_id, entity_type, event_id, event_type, next_status, previous_status, occurred_at, reason)
VALUES
(gen_random_uuid(), 'SYSTEM', 'f975f30d-a365-4bc2-aca0-b7a35aae9b78', 'QUEUE_TICKET', gen_random_uuid(), 'LEAVE_BEFORE_EXAM', 'LEFT_BEFORE_EXAM', 'CALLED', CURRENT_TIMESTAMP, 'Tự động dọn sau 24h không hoàn tất'),
(gen_random_uuid(), 'SYSTEM', '139a6cac-41d5-4df6-a62a-c25db326fb8f', 'APPOINTMENT', gen_random_uuid(), 'LEAVE_BEFORE_EXAM', 'NOT_PERFORMED', 'CHECKED_IN', CURRENT_TIMESTAMP, 'Tự động dọn sau 24h không hoàn tất'),
(gen_random_uuid(), 'SYSTEM', 'fdcc93fb-1efc-4708-9ec9-179eeefdbcd4', 'EXAMINATION', gen_random_uuid(), 'LEAVE_BEFORE_EXAM', 'CANCELLED', 'SCHEDULED', CURRENT_TIMESTAMP, 'Tự động dọn sau 24h không hoàn tất'),
(gen_random_uuid(), 'SYSTEM', '2ddd8f73-0984-4464-8c1a-b8b951e9d48d', 'EXAMINATION', gen_random_uuid(), 'LEAVE_BEFORE_EXAM', 'CANCELLED', 'SCHEDULED', CURRENT_TIMESTAMP, 'Đồng bộ trạng thái hủy phiên khám'),
(gen_random_uuid(), 'SYSTEM', 'b238dc59-4468-4dc5-b0f9-367c47a0f2b6', 'APPOINTMENT', gen_random_uuid(), 'LEAVE_BEFORE_EXAM', 'NOT_PERFORMED', 'BOOKED', CURRENT_TIMESTAMP, 'Đồng bộ trạng thái rời trước khám'),
(gen_random_uuid(), 'SYSTEM', '53891360-0748-4a09-8a8b-3178f05b4a53', 'APPOINTMENT', gen_random_uuid(), 'COMPLETE_EXAMINATION', 'COMPLETED', 'BOOKED', CURRENT_TIMESTAMP, 'Đồng bộ trạng thái hoàn tất khám')
ON CONFLICT DO NOTHING;

-- 3. Close the false reconciliation incidents
UPDATE reconciliation_incidents
SET status = 'CLOSED',
    closed_at = CURRENT_TIMESTAMP,
    closed_by = 'migration_v45',
    resolution_action = 'NO_ACTION_REQUIRED',
    reference_type = 'BUSINESS_LOG',
    result_note = 'Sự cố đối soát giả phát sinh do nhật ký trạng thái lệch hoặc thiếu lượt ghi; đã xử lý đồng bộ logic.'
WHERE incident_code IN (
    'SC-202610-FA53CA',
    'SC-202610-29ADC6',
    'SC-202609-08BECD',
    'SC-202610-41EA5E',
    'SC-202610-4D2C83'
) AND status = 'OPEN';

-- 4. Backfill missing clinic_service_id on historical walk-in appointments matching active specialty
UPDATE appointments a
SET clinic_service_id = cs.id,
    service_name = cs.name,
    visit_type = cs.visit_type,
    service_duration_minutes = cs.duration_minutes,
    requires_medical_record = cs.requires_medical_record
FROM clinic_services cs
WHERE a.clinic_service_id IS NULL
  AND cs.active = true
  AND lower(btrim(cs.specialty)) = lower(btrim(a.specialty));

-- 5. Backfill follow_up_date for medical records where only follow_up_days was recorded
UPDATE medical_records mr
SET follow_up_date = a.appointment_date + mr.follow_up_days
FROM examination_sessions es
JOIN appointments a ON a.id = es.appointment_id
WHERE mr.examination_session_id = es.id
  AND mr.follow_up_days IS NOT NULL
  AND mr.follow_up_date IS NULL;

-- 6. Close stale reschedule cases where the underlying appointment has ended
UPDATE appointment_reschedule_cases rc
SET status = 'RESOLVED',
    resolved_at = CURRENT_TIMESTAMP,
    reason = SUBSTRING(CONCAT(rc.reason, ' [Tự động đóng do lịch gốc đã kết thúc: ', a.status, ']') FROM 1 FOR 500)
FROM appointments a
WHERE rc.appointment_id = a.id
  AND rc.status = 'OPEN'
  AND a.status <> 'BOOKED';
