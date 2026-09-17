-- Migration V24: Seed standard specialties and ICD-10 diagnosis catalog
-- Idempotent insert with WHERE NOT EXISTS checks

-- 1. Specialties Catalog (12 clinical specialties)
INSERT INTO specialties (id, code, name, description, active, created_at, updated_at)
SELECT gen_random_uuid(), val.code, val.name, val.description, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM (VALUES
    ('NOI', 'Khám Tổng Quát', 'Khám và tầm soát tổng quát, được hướng dẫn tới đúng chuyên khoa khi cần.'),
    ('TIM', 'Khám Tim Mạch', 'Đau ngực, hồi hộp, khó thở, huyết áp hoặc mỡ máu.'),
    ('HO_HAP', 'Khám Hô Hấp', 'Ho kéo dài, khó thở, khò khè hoặc các vấn đề về phổi.'),
    ('TIEU_HOA', 'Khám Tiêu Hoá - Gan Mật', 'Đau bụng, ợ hơi, trào ngược, rối loạn tiêu hoá hoặc bệnh lý gan mật.'),
    ('NHI', 'Khám Nhi Khoa', 'Khám sức khỏe, theo dõi phát triển và tiêm chủng cho trẻ em.'),
    ('TMH', 'Khám Tai Mũi Họng', 'Đau họng, nghẹt mũi, viêm xoang, ù tai hoặc nghe kém.'),
    ('MAT', 'Khám Mắt', 'Mờ mắt, đau mắt, đỏ mắt, cộm ngứa hoặc các bệnh lý về mắt.'),
    ('TK', 'Khám Thần Kinh', 'Đau đầu, chóng mặt, mất ngủ, tê bì hoặc đau cổ vai gáy.'),
    ('XK', 'Khám Xương Khớp', 'Đau khớp, đau lưng, chấn thương thể thao hoặc hạn chế vận động.'),
    ('DL', 'Khám Da Liễu', 'Mụn, ngứa, nổi mẩn, nấm da, rụng tóc hoặc bất thường trên da.'),
    ('PK', 'Khám Phụ Khoa', 'Tư vấn và thăm khám các vấn đề phụ khoa thường gặp.'),
    ('NT', 'Khám Nội Tiết', 'Theo dõi tiểu đường, tuyến giáp và các rối loạn nội tiết.')
) AS val(code, name, description)
WHERE NOT EXISTS (
    SELECT 1 FROM specialties s WHERE s.code = val.code OR s.name = val.name
);

-- 2. Diagnosis Catalog (Common ICD-10 medical diagnoses)
INSERT INTO diagnosis_catalog (id, code, name, active, created_at, updated_at)
SELECT gen_random_uuid(), val.code, val.name, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM (VALUES
    ('J00', 'J00 - Viêm mũi họng cấp tính (cảm thường)'),
    ('J01', 'J01 - Viêm xoang cấp tính'),
    ('J02', 'J02 - Viêm họng cấp tính'),
    ('J03', 'J03 - Viêm amiđan cấp tính'),
    ('J04', 'J04 - Viêm thanh quản và khí quản cấp tính'),
    ('J06', 'J06 - Nhiễm khuẩn hô hấp trên cấp tính ở nhiều vị trí'),
    ('J15', 'J15 - Viêm phổi do vi khuẩn'),
    ('J18', 'J18 - Viêm phổi không đặc hiệu'),
    ('J20', 'J20 - Viêm phế quản cấp tính'),
    ('J44', 'J44 - Bệnh phổi tắc nghẽn mạn tính (COPD)'),
    ('J45', 'Hen phế quản (Suyễn)'),
    ('I10', 'I10 - Tăng huyết áp vô căn (nguyên phát)'),
    ('I11', 'I11 - Bệnh tim do tăng huyết áp'),
    ('I20', 'I20 - Cơn đau thắt ngực (thiếu máu cơ tim)'),
    ('I25', 'I25 - Bệnh tim thiếu máu cục bộ mạn tính'),
    ('I48', 'I48 - Rung nhĩ và cuồng nhĩ'),
    ('I50', 'I50 - Suy tim'),
    ('I83', 'I83 - Giãn tĩnh mạch chi dưới'),
    ('K21', 'K21 - Bệnh trào ngược dạ dày - thực quản (GERD)'),
    ('K25', 'K25 - Loét dạ dày'),
    ('K26', 'K26 - Loét tá tràng'),
    ('K29', 'K29 - Viêm dạ dày và tá tràng'),
    ('K30', 'K30 - Chứng khó tiêu chức năng'),
    ('K58', 'K58 - Hội chứng ruột kích thích (IBS)'),
    ('K59', 'K59 - Táo bón mạn tính'),
    ('K76.0', 'K76.0 - Gan nhiễm mỡ không do rượu'),
    ('K80', 'K80 - Sỏi mật'),
    ('E11', 'E11 - Đái tháo đường không phụ thuộc insulin (Type 2)'),
    ('E78', 'E78 - Rối loạn chuyển hóa lipoprotein và tăng lipid máu'),
    ('E79', 'E79 - Tăng acid uric máu / Bệnh Gút'),
    ('E03', 'E03 - Suy tuyến giáp khác'),
    ('E05', 'E05 - Nhiễm độc giáp (Cường giáp)'),
    ('N39.0', 'N39.0 - Nhiễm trùng đường tiết niệu vị trí không xác định'),
    ('M17', 'M17 - Thoái hóa khớp gối'),
    ('M19', 'M19 - Thoái hóa các khớp khác'),
    ('M54.5', 'M54.5 - Đau vùng thắt lưng'),
    ('M54.2', 'M54.2 - Đau vùng cổ và vai gáy'),
    ('M10', 'M10 - Bệnh Gút cấp và mạn tính'),
    ('M81', 'M81 - Loãng xương không kèm gãy xương bệnh lý'),
    ('G43', 'G43 - Đau nửa đầu (Migraine)'),
    ('G44', 'G44 - Các hội chứng đau đầu khác (Đau đầu căng thẳng)'),
    ('G47', 'G47 - Rối loạn giấc ngủ (Mất ngủ)'),
    ('H81', 'H81 - Rối loạn chức năng tiền đình (Chóng mặt tiền đình)'),
    ('H10', 'H10 - Viêm kết mạc (Đau mắt đỏ)'),
    ('H52', 'H52 - Tật khúc xạ và điều tiết (Cận/loạn/viễn thị)'),
    ('H66', 'H66 - Viêm tai giữa có mủ và không mủ'),
    ('L20', 'L20 - Viêm da cơ địa (Eczema)'),
    ('L50', 'L50 - Mày đay dị ứng'),
    ('Z00.0', 'Z00.0 - Khám sức khỏe tổng quát định kỳ'),
    ('Z01.0', 'Z01.0 - Khám và kiểm tra mắt và thị lực'),
    ('Z71.3', 'Z71.3 - Tư vấn và giám sát chế độ ăn kiêng')
) AS val(code, name)
WHERE NOT EXISTS (
    SELECT 1 FROM diagnosis_catalog d WHERE d.code = val.code
);
