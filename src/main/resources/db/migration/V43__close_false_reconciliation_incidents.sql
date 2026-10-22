-- V43: Close historical false-positive reconciliation incidents
-- 1. False hash chain incidents caused by same-second UUID order mismatch in legacy verifier
UPDATE reconciliation_incidents
SET status = 'CLOSED',
    closed_at = NOW(),
    closed_by = 'system_migration_v43',
    resolution_action = 'NO_ACTION_REQUIRED',
    result_note = 'Closed false-positive hash chain incident caused by same-second UUID order mismatch prior to ordering stabilization.'
WHERE entity_type = 'BUSINESS_LOG'
  AND status = 'OPEN'
  AND reason LIKE 'Chuỗi hash nhật ký nghiệp vụ không hợp lệ%';

-- 2. False incidents for entity types (INVOICE, RESCHEDULE_CASE) missing lookup in legacy integrity job
UPDATE reconciliation_incidents
SET status = 'CLOSED',
    closed_at = NOW(),
    closed_by = 'system_migration_v43',
    resolution_action = 'NO_ACTION_REQUIRED',
    result_note = 'Closed false-positive discrepancy incident for entity type without status lookup in legacy integrity job.'
WHERE entity_type IN ('INVOICE', 'RESCHEDULE_CASE')
  AND status = 'OPEN'
  AND reason LIKE 'Không tìm thấy dữ liệu % cho thao tác nghiệp vụ%';
