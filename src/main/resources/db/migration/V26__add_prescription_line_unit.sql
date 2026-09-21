-- V26__add_prescription_line_unit.sql
-- Bổ sung trường đơn vị tính (unit) cho từng dòng thuốc trong đơn thuốc

ALTER TABLE prescription_lines
    ADD COLUMN IF NOT EXISTS unit VARCHAR(50);

-- Cập nhật đơn vị tính cho các dòng thuốc đã có liên kết với danh mục thuốc
UPDATE prescription_lines pl
SET unit = mc.unit
FROM medication_catalog mc
WHERE pl.source_medication_id = mc.id
  AND pl.unit IS NULL
  AND mc.unit IS NOT NULL;
