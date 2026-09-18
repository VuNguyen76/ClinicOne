-- Migration V25: Purge legacy corrupt midnight schedule templates and slots
-- Ensure all doctor shifts run during standard clinic operating hours

-- 1. Deactivate any legacy template starting before 07:00 AM (midnight)
UPDATE work_schedule_templates
SET active = false, updated_at = CURRENT_TIMESTAMP
WHERE day_start < '07:00:00';

-- 2. Remove open slots that were generated for midnight hours (before 07:00 AM)
DELETE FROM generated_clinic_slots
WHERE status = 'OPEN' AND start_time < '07:00:00';
