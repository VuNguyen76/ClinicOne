-- Migration V27: Clean overlapping active doctor_schedules by keeping the latest created row
-- Per doctor_profile_id + day_of_week, if multiple active rows overlap the KEPT (rn=1) row,
-- deactivate them. Gap schedules (e.g. 08:00-12:00 and 13:00-17:00) are NOT affected
-- because they don't overlap the kept row.
-- This fixes legacy overlapping shifts (e.g., 08:00-17:00 + 08:00-21:00 on same weekday)
-- without deleting rows, preserving full data history for audit/recovery.
--
-- Example before V27: doctor02 FRIDAY has 08:00-21:00 (2026-09-18) and 08:00-17:00 (2026-09-23) both active
-- Example after V27: only 08:00-17:00 (2026-09-23) remains active, 08:00-21:00 (2026-09-18) becomes inactive
-- Example: MONDAY 08:00-17:00 (newest) + 19:00-23:00 + 21:00-23:30 -> keeps 08:00-17:00 AND 19:00-23:00,
-- only 21:00-23:30 (overlaps kept/evening rows inconsistently) may be deactivated if it overlaps the keeper.
-- NOTE: Gap schedules (e.g., 08:00-12:00 and 13:00-17:00) are NOT affected because they don't overlap the keeper.

UPDATE doctor_schedules
SET active = false
WHERE id IN (
    SELECT t.id
    FROM (
        SELECT
            id,
            doctor_profile_id,
            day_of_week,
            start_time,
            end_time,
            ROW_NUMBER() OVER (
                PARTITION BY doctor_profile_id, day_of_week
                ORDER BY created_at DESC
            ) AS rn
        FROM doctor_schedules
        WHERE active = true
    ) t
    JOIN (
        SELECT doctor_profile_id, day_of_week, start_time AS k_start, end_time AS k_end
        FROM (
            SELECT
                doctor_profile_id,
                day_of_week,
                start_time,
                end_time,
                ROW_NUMBER() OVER (
                    PARTITION BY doctor_profile_id, day_of_week
                    ORDER BY created_at DESC
                ) AS rn
            FROM doctor_schedules
            WHERE active = true
        ) k
        WHERE k.rn = 1
    ) keeper
      ON keeper.doctor_profile_id = t.doctor_profile_id
     AND keeper.day_of_week = t.day_of_week
    WHERE t.rn > 1
      AND keeper.k_start < t.end_time
      AND t.start_time < keeper.k_end
);
