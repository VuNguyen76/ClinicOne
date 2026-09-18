-- V23__add_medication_units_and_dosages.sql
-- Danh mục Đơn vị tính và Liều dùng chuẩn cho nghiệp vụ Dược & Kê đơn

CREATE TABLE IF NOT EXISTS medication_units (
    id UUID PRIMARY KEY,
    code VARCHAR(50) NOT NULL,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(255) NOT NULL DEFAULT '',
    active BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_medication_unit_code UNIQUE (code),
    CONSTRAINT uk_medication_unit_name UNIQUE (name)
);

CREATE TABLE IF NOT EXISTS medication_dosages (
    id UUID PRIMARY KEY,
    code VARCHAR(50) NOT NULL,
    unit_name VARCHAR(100) NOT NULL,
    dosage_format VARCHAR(200) NOT NULL,
    description VARCHAR(255) NOT NULL DEFAULT '',
    active BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_medication_dosage_code UNIQUE (code)
);

-- Seed 12 đơn vị tính chuẩn y tế
INSERT INTO medication_units (id, code, name, description, active, sort_order) VALUES
    (gen_random_uuid(), 'UNIT-VIEN', 'Viên', 'Dạng rắn: viên nén, viên bao phim, viên nang, viên sủi', true, 1),
    (gen_random_uuid(), 'UNIT-GOI', 'Gói', 'Dạng bột hoặc cốm pha hỗn dịch/dung dịch uống', true, 2),
    (gen_random_uuid(), 'UNIT-CHAI', 'Chai', 'Dung dịch uống, siro, cồn sát khuẩn, nước súc họng', true, 3),
    (gen_random_uuid(), 'UNIT-LO', 'Lọ', 'Thuốc nhỏ mắt, mũi, tai hoặc dung dịch tiêm truyền', true, 4),
    (gen_random_uuid(), 'UNIT-ONG', 'Ống', 'Dung dịch uống, hỗn dịch khí dung hoặc ống tiêm', true, 5),
    (gen_random_uuid(), 'UNIT-TUYP', 'Tuýp', 'Dạng kem, mỡ, gel dùng ngoài bôi da', true, 6),
    (gen_random_uuid(), 'UNIT-VI', 'Vỉ', 'Vỉ thuốc nén hoặc nang', true, 7),
    (gen_random_uuid(), 'UNIT-HOP', 'Hộp', 'Quy cách đóng gói hộp nguyên', true, 8),
    (gen_random_uuid(), 'UNIT-TUI', 'Túi', 'Túi dịch truyền hoặc túi bột', true, 9),
    (gen_random_uuid(), 'UNIT-BINH-XIT', 'Bình xịt', 'Thuốc xịt mũi, xịt họng hoặc bình hít định liều', true, 10),
    (gen_random_uuid(), 'UNIT-MIENG-DAN', 'Miếng dán', 'Miếng dán thẩm thấu qua da giảm đau hoặc hạ sốt', true, 11),
    (gen_random_uuid(), 'UNIT-GIOT', 'Giọt', 'Dung dịch đậm đặc nhỏ giọt', true, 12)
ON CONFLICT (code) DO NOTHING;

-- Seed liều dùng chuẩn y tế theo từng đơn vị tính
INSERT INTO medication_dosages (id, code, unit_name, dosage_format, description, active, sort_order) VALUES
    -- Định dạng cho Viên
    (gen_random_uuid(), 'DOS-VIEN-2X', 'Viên', '1 viên/lần x 2 lần/ngày (Sáng 1, Tối 1)', 'Dùng 2 lần sáng và tối sau ăn', true, 1),
    (gen_random_uuid(), 'DOS-VIEN-3X', 'Viên', '1 viên/lần x 3 lần/ngày (Sáng 1, Trưa 1, Tối 1)', 'Dùng 3 lần mỗi ngày', true, 2),
    (gen_random_uuid(), 'DOS-VIEN-1X-S', 'Viên', '1 viên/lần x 1 lần/ngày (Sáng 1)', 'Dùng 1 lần cố định buổi sáng', true, 3),
    (gen_random_uuid(), 'DOS-VIEN-1X-T', 'Viên', '1 viên/lần x 1 lần/ngày (Tối 1 trước khi ngủ)', 'Dùng 1 lần cố định buổi tối', true, 4),
    (gen_random_uuid(), 'DOS-VIEN-PAIN', 'Viên', '2 viên/lần khi sốt/đau (cách nhau 4-6 giờ, tối đa 4 lần/ngày)', 'Dùng khi có triệu chứng sốt hoặc đau', true, 5),

    -- Định dạng cho Gói
    (gen_random_uuid(), 'DOS-GOI-2X', 'Gói', '1 gói/lần x 2 lần/ngày (Sáng 1, Tối 1)', 'Pha với nước ấm uống sáng, tối', true, 10),
    (gen_random_uuid(), 'DOS-GOI-3X', 'Gói', '1 gói/lần x 3 lần/ngày (Sáng 1, Trưa 1, Tối 1)', 'Pha nước uống 3 lần/ngày', true, 11),
    (gen_random_uuid(), 'DOS-GOI-1X', 'Gói', '1 gói/lần x 1 lần/ngày (Sáng 1)', 'Pha nước uống vào buổi sáng', true, 12),
    (gen_random_uuid(), 'DOS-GOI-PAIN', 'Gói', '1 gói/lần khi đau/sốt (cách tối thiểu 4-6 giờ)', 'Pha với nước ấm khi sốt hoặc đau', true, 13),
    (gen_random_uuid(), 'DOS-GOI-MIX', 'Gói', '1-2 gói/ngày chia 2 lần pha nước', 'Hòa tan hoàn toàn trước khi uống', true, 14),

    -- Định dạng cho Ống / Chai / Lọ
    (gen_random_uuid(), 'DOS-ONG-2X', 'Ống', '1 ống/lần x 2 lần/ngày (Sáng 1, Tối 1)', 'Lắc đều trước khi bẻ ống uống', true, 20),
    (gen_random_uuid(), 'DOS-ONG-1X', 'Ống', '1 ống/lần x 1 lần/ngày (Sáng 1)', 'Uống 1 ống vào buổi sáng', true, 21),
    (gen_random_uuid(), 'DOS-ML-23X', 'Chai', '5 ml/lần x 2-3 lần/ngày', 'Đo bằng cốc chia vạch đi kèm', true, 22),
    (gen_random_uuid(), 'DOS-ML-2X', 'Chai', '10 ml/lần x 2 lần/ngày sau ăn', 'Uống sau ăn', true, 23),
    (gen_random_uuid(), 'DOS-LO-EYE', 'Lọ', 'Nhỏ 1-2 giọt vào mắt bị bệnh, ngày 3-4 lần', 'Nhỏ mắt cách nhau 4-6 giờ', true, 24),

    -- Định dạng cho Tuýp (dạng kem, mỡ, gel)
    (gen_random_uuid(), 'DOS-TUYP-2X', 'Tuýp', 'Bôi 1 lớp mỏng 2 lần/ngày (Sáng, Tối)', 'Vệ sinh sạch vùng tổn thương trước khi thoa', true, 30),
    (gen_random_uuid(), 'DOS-TUYP-3X', 'Tuýp', 'Bôi 1 lớp mỏng 3 lần/ngày', 'Thoa nhẹ nhàng, tránh tiếp xúc mắt', true, 31),
    (gen_random_uuid(), 'DOS-TUYP-SKIN', 'Tuýp', 'Thoa nhẹ lên vùng da tổn thương 1-2 lần/ngày', 'Dùng ngoài da', true, 32),

    -- Định dạng cho Giọt
    (gen_random_uuid(), 'DOS-GIOT-3X', 'Giọt', '1-2 giọt/lần x 3 lần/ngày', 'Nhỏ trực tiếp hoặc pha chút nước', true, 40),
    (gen_random_uuid(), 'DOS-GIOT-2X', 'Giọt', '2-3 giọt/lần x 2 lần/ngày', 'Nhỏ theo chỉ dẫn', true, 41),

    -- Định dạng cho Bình xịt
    (gen_random_uuid(), 'DOS-XIT-2X', 'Bình xịt', 'Xịt 1-2 nhát/lần x 2 lần/ngày', 'Xịt sau khi vệ sinh sạch khoang mũi/họng', true, 50),
    (gen_random_uuid(), 'DOS-XIT-NOSE', 'Bình xịt', 'Xịt 1 nhát vào mỗi bên mũi x 2 lần/ngày', 'Hít nhẹ khi ấn đầu xịt', true, 51),
    (gen_random_uuid(), 'DOS-XIT-BREATH', 'Bình xịt', 'Xịt 2 nhát khi khó thở (cách tối thiểu 4 giờ)', 'Bình xịt định liều', true, 52),

    -- Định dạng cho Miếng dán
    (gen_random_uuid(), 'DOS-DAN-1X', 'Miếng dán', 'Dán 1 miếng/ngày (thay sau 24 giờ)', 'Dán lên vùng da khô sạch không trầy xước', true, 60),
    (gen_random_uuid(), 'DOS-DAN-PAIN', 'Miếng dán', 'Dán 1 miếng khi đau (tối đa 8 giờ)', 'Gỡ bỏ sau tối đa 8 giờ sử dụng', true, 61)
ON CONFLICT (code) DO NOTHING;
