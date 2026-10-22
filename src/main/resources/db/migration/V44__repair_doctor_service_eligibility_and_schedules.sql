-- V44: Repair doctor and service eligibility plus weekly schedules.
--
-- Two defects made doctors effectively invisible in the clinic:
--
--   1. clinic_service_doctors was only ever written by the service management
--      screen, so a doctor created afterwards was missing from every explicit
--      eligible list. The scheduling screen hid that doctor from the picker,
--      availability search filtered the doctor's slots out, and the template
--      API rejected the request with SCHEDULE_DOCTOR_NOT_ELIGIBLE.
--
--   2. Only 2 of 111 doctor_schedules rows were still active, because older
--      overlapping-template cleanup passes deactivated every row. The weekly
--      schedule fallback used by the availability service therefore returned
--      nothing for almost every specialty.

-- 1. Link active doctors that are not linked to any service at all.
INSERT INTO clinic_service_doctors (service_id, doctor_profile_id)
SELECT s.id, p.id
FROM clinic_services s
JOIN doctor_profiles p
  ON lower(btrim(p.specialty)) = lower(btrim(s.specialty))
WHERE s.active = true
  AND p.active = true
  AND EXISTS (SELECT 1 FROM clinic_service_doctors d WHERE d.service_id = s.id)
  AND NOT EXISTS (SELECT 1 FROM clinic_service_doctors d WHERE d.doctor_profile_id = p.id)
ON CONFLICT DO NOTHING;

-- 2. Restore one active weekly shift per doctor and weekday when every row for
--    that day was deactivated by an earlier overlapping-shift cleanup.
UPDATE doctor_schedules ds
SET active = true
WHERE ds.id IN (
    SELECT DISTINCT ON (x.doctor_profile_id, x.day_of_week) x.id
    FROM doctor_schedules x
    WHERE x.start_time >= TIME '07:00:00'
      AND x.end_time > x.start_time
      AND NOT EXISTS (
          SELECT 1
          FROM doctor_schedules y
          WHERE y.doctor_profile_id = x.doctor_profile_id
            AND y.day_of_week = x.day_of_week
            AND y.active = true)
    ORDER BY x.doctor_profile_id, x.day_of_week,
             (x.end_time - x.start_time) DESC, x.created_at DESC
);

-- 3. Give active doctors a default weekday shift when they have none at all.
INSERT INTO doctor_schedules (id, active, created_at, day_of_week, end_time,
                              slot_duration_minutes, start_time, doctor_profile_id)
SELECT gen_random_uuid(), true, CURRENT_TIMESTAMP, d.day_of_week, TIME '17:00:00',
       30, TIME '08:00:00', p.id
FROM doctor_profiles p
CROSS JOIN (VALUES ('MONDAY'), ('TUESDAY'), ('WEDNESDAY'), ('THURSDAY'), ('FRIDAY'))
     AS d(day_of_week)
WHERE p.active = true
  AND NOT EXISTS (SELECT 1 FROM doctor_schedules s WHERE s.doctor_profile_id = p.id);
