-- Migration V40: Add clinical booking reasons table per specialty
CREATE TABLE IF NOT EXISTS clinical_reasons (
    id UUID PRIMARY KEY,
    specialty_code VARCHAR(20) NOT NULL,
    specialty_name VARCHAR(120) NOT NULL,
    symptom_name VARCHAR(200) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_clinical_reasons_specialty_symptom UNIQUE (specialty_name, symptom_name)
);

CREATE INDEX IF NOT EXISTS idx_clinical_reasons_specialty
    ON clinical_reasons (specialty_name, active, sort_order ASC);

-- Seed clinical symptoms & consultation reasons for all 7 clinic specialties
INSERT INTO clinical_reasons (id, specialty_code, specialty_name, symptom_name, sort_order, active, created_at, updated_at)
VALUES
    -- 1. Khám Tổng Quát
    (gen_random_uuid(), 'NOI', 'Khám Tổng Quát', 'Khám sức khỏe tổng quát định kỳ', 1, TRUE, NOW(), NOW()),
    (gen_random_uuid(), 'NOI', 'Khám Tổng Quát', 'Tầm soát xét nghiệm máu và đường huyết', 2, TRUE, NOW(), NOW()),
    (gen_random_uuid(), 'NOI', 'Khám Tổng Quát', 'Mệt mỏi kéo dài, sụt cân chưa rõ nguyên nhân', 3, TRUE, NOW(), NOW()),
    (gen_random_uuid(), 'NOI', 'Khám Tổng Quát', 'Tái khám kiểm tra sức khỏe người cao tuổi', 4, TRUE, NOW(), NOW()),

    -- 2. Khám Tim Mạch
    (gen_random_uuid(), 'TIM', 'Khám Tim Mạch', 'Tăng huyết áp, kiểm tra số đo định kỳ', 1, TRUE, NOW(), NOW()),
    (gen_random_uuid(), 'TIM', 'Khám Tim Mạch', 'Hồi hộp đánh trống ngực, nhịp tim không đều', 2, TRUE, NOW(), NOW()),
    (gen_random_uuid(), 'TIM', 'Khám Tim Mạch', 'Đau tức ngực trái khi gắng sức hoặc leo cầu thang', 3, TRUE, NOW(), NOW()),
    (gen_random_uuid(), 'TIM', 'Khám Tim Mạch', 'Khó thở khi nằm, phù nhẹ hai mắt cá chân', 4, TRUE, NOW(), NOW()),

    -- 3. Khám Hô Hấp
    (gen_random_uuid(), 'HO_HAP', 'Khám Hô Hấp', 'Ho khan kéo dài nhiều tuần về đêm', 1, TRUE, NOW(), NOW()),
    (gen_random_uuid(), 'HO_HAP', 'Khám Hô Hấp', 'Ho có đờm, khò khè, khó thở khi thay đổi thời tiết', 2, TRUE, NOW(), NOW()),
    (gen_random_uuid(), 'HO_HAP', 'Khám Hô Hấp', 'Tức ngực, rát họng, khàn tiếng', 3, TRUE, NOW(), NOW()),
    (gen_random_uuid(), 'HO_HAP', 'Khám Hô Hấp', 'Tái khám theo dõi hen suyễn hoặc viêm phế quản', 4, TRUE, NOW(), NOW()),

    -- 4. Khám Tiêu Hoá - Gan Mật
    (gen_random_uuid(), 'TIEU_HOA', 'Khám Tiêu Hoá - Gan Mật', 'Đau âm ỉ vùng thượng vị sau ăn hoặc khi đói', 1, TRUE, NOW(), NOW()),
    (gen_random_uuid(), 'TIEU_HOA', 'Khám Tiêu Hoá - Gan Mật', 'Ợ chua, nóng rát cổ họng, trào ngược GERD', 2, TRUE, NOW(), NOW()),
    (gen_random_uuid(), 'TIEU_HOA', 'Khám Tiêu Hoá - Gan Mật', 'Đầy bụng khó tiêu, chướng bụng sau ăn dầu mỡ', 3, TRUE, NOW(), NOW()),
    (gen_random_uuid(), 'TIEU_HOA', 'Khám Tiêu Hoá - Gan Mật', 'Đau tức hạ sườn phải, kiểm tra men gan định kỳ', 4, TRUE, NOW(), NOW()),

    -- 5. Khám Nhi Khoa
    (gen_random_uuid(), 'NHI', 'Khám Nhi Khoa', 'Bé sốt nhẹ đến vừa, quấy khóc, biếng ăn', 1, TRUE, NOW(), NOW()),
    (gen_random_uuid(), 'NHI', 'Khám Nhi Khoa', 'Bé ho húng hắng, sổ mũi, nghẹt mũi', 2, TRUE, NOW(), NOW()),
    (gen_random_uuid(), 'NHI', 'Khám Nhi Khoa', 'Rối loạn tiêu hóa, nôn trớ, đi ngoài phân lỏng', 3, TRUE, NOW(), NOW()),
    (gen_random_uuid(), 'NHI', 'Khám Nhi Khoa', 'Khám tổng quát học đường và tư vấn dinh dưỡng cho trẻ', 4, TRUE, NOW(), NOW()),

    -- 6. Khám Tai Mũi Họng
    (gen_random_uuid(), 'TMH', 'Khám Tai Mũi Họng', 'Đau rát họng, nuốt vướng, amidan sưng đỏ', 1, TRUE, NOW(), NOW()),
    (gen_random_uuid(), 'TMH', 'Khám Tai Mũi Họng', 'Nghẹt mũi kéo dài, chảy nước mũi, giảm khứu giác', 2, TRUE, NOW(), NOW()),
    (gen_random_uuid(), 'TMH', 'Khám Tai Mũi Họng', 'Đau nhức vùng xoang trán hoặc hai bên gò má', 3, TRUE, NOW(), NOW()),
    (gen_random_uuid(), 'TMH', 'Khám Tai Mũi Họng', 'Ù tai, đau tai, chảy dịch tai hoặc giảm thính lực', 4, TRUE, NOW(), NOW()),

    -- 7. Khám Mắt
    (gen_random_uuid(), 'MAT', 'Khám Mắt', 'Mắt mờ nhìn xa không rõ, nghi ngờ tật khúc xạ', 1, TRUE, NOW(), NOW()),
    (gen_random_uuid(), 'MAT', 'Khám Mắt', 'Mỏi mắt, khô mắt, cộm xốn khi dùng máy tính lâu', 2, TRUE, NOW(), NOW()),
    (gen_random_uuid(), 'MAT', 'Khám Mắt', 'Đỏ mắt, chảy nước mắt sống, ghèn mắt nhiều', 3, TRUE, NOW(), NOW()),
    (gen_random_uuid(), 'MAT', 'Khám Mắt', 'Kiểm tra thị lực và đo độ kính cận - viễn - loạn', 4, TRUE, NOW(), NOW())
ON CONFLICT (specialty_name, symptom_name) DO NOTHING;
