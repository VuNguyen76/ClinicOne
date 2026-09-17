-- V20__add_medication_clinical_metadata.sql
-- Thêm các cột metadata lâm sàng vào bảng medication_catalog

ALTER TABLE medication_catalog
    ADD COLUMN IF NOT EXISTS category VARCHAR(100),
    ADD COLUMN IF NOT EXISTS specialties VARCHAR(255),
    ADD COLUMN IF NOT EXISTS default_dosage VARCHAR(150),
    ADD COLUMN IF NOT EXISTS default_instructions VARCHAR(500),
    ADD COLUMN IF NOT EXISTS unit VARCHAR(50);

-- Cập nhật dữ liệu chuẩn cho 54 loại thuốc
UPDATE medication_catalog SET
    category = 'Hô hấp',
    specialties = 'Khám Hô Hấp, Khám Nhi Khoa, Khám Tai Mũi Họng, Khám Tổng Quát',
    unit = 'Gói',
    default_dosage = '1 gói (hoặc 1 viên) / lần x 2-3 lần/ngày',
    default_instructions = 'Pha với 1 cốc nước ấm, uống sau khi ăn'
WHERE code = 'MED-ACETYL-200';

UPDATE medication_catalog SET
    category = 'Hô hấp',
    specialties = 'Khám Hô Hấp, Khám Nhi Khoa, Khám Tai Mũi Họng, Khám Tổng Quát',
    unit = 'Viên',
    default_dosage = '1 viên / lần x 3 lần/ngày',
    default_instructions = 'Uống với nhiều nước sau bữa ăn'
WHERE code = 'MED-AMBROX-30';

UPDATE medication_catalog SET
    category = 'Tim mạch',
    specialties = 'Khám Tim Mạch, Khám Tổng Quát',
    unit = 'Viên',
    default_dosage = '1 viên / lần / ngày',
    default_instructions = 'Uống vào buổi sáng cố định mỗi ngày'
WHERE code = 'MED-AMLO-5';

UPDATE medication_catalog SET
    category = 'Kháng sinh',
    specialties = 'Khám Hô Hấp, Khám Tai Mũi Họng, Khám Tổng Quát, Khám Nhi Khoa',
    unit = 'Viên',
    default_dosage = '1 viên / lần x 2-3 lần/ngày',
    default_instructions = 'Uống trước hoặc sau ăn, cách đều mỗi 8 tiếng'
WHERE code = 'MED-AMOX-500';

UPDATE medication_catalog SET
    category = 'Tim mạch',
    specialties = 'Khám Tim Mạch, Khám Tổng Quát',
    unit = 'Viên',
    default_dosage = '1 viên / ngày',
    default_instructions = 'Uống sau bữa ăn no, uống nguyên viên không nhai'
WHERE code = 'MED-ASPIRIN-81';

UPDATE medication_catalog SET
    category = 'Kháng sinh',
    specialties = 'Khám Hô Hấp, Khám Tai Mũi Họng, Khám Tiêu Hoá - Gan Mật, Khám Tổng Quát',
    unit = 'Viên',
    default_dosage = '1 viên / lần x 2 lần/ngày',
    default_instructions = 'Uống ngay trước bữa ăn để hạn chế khó chịu dạ dày'
WHERE code = 'MED-AUGM-1000';

UPDATE medication_catalog SET
    category = 'Kháng sinh',
    specialties = 'Khám Hô Hấp, Khám Tai Mũi Họng, Khám Tổng Quát',
    unit = 'Viên',
    default_dosage = '1 viên / ngày x 3 ngày liên tục',
    default_instructions = 'Uống 1 giờ trước khi ăn hoặc 2 giờ sau ăn'
WHERE code = 'MED-AZIT-500';

UPDATE medication_catalog SET
    category = 'Tiêu hóa',
    specialties = 'Khám Tiêu Hoá - Gan Mật, Khám Tổng Quát',
    unit = 'Viên',
    default_dosage = '2-4 viên / lần x 2 lần/ngày',
    default_instructions = 'Uống trước bữa ăn với nước ấm'
WHERE code = 'MED-BERB-100';

UPDATE medication_catalog SET
    category = 'Vitamin & Khoáng chất',
    specialties = 'Khám Tổng Quát, Khám Nhi Khoa, Khám Tim Mạch',
    unit = 'Viên',
    default_dosage = '1 viên / ngày',
    default_instructions = 'Uống vào buổi sáng sau ăn với nhiều nước'
WHERE code = 'MED-CALCI-D3';

UPDATE medication_catalog SET
    category = 'Kháng sinh',
    specialties = 'Khám Hô Hấp, Khám Tai Mũi Họng, Khám Tổng Quát, Khám Nhi Khoa',
    unit = 'Viên',
    default_dosage = '1-2 viên / ngày chia 1-2 lần',
    default_instructions = 'Uống sau bữa ăn'
WHERE code = 'MED-CEFI-200';

UPDATE medication_catalog SET
    category = 'Kháng sinh',
    specialties = 'Khám Hô Hấp, Khám Tai Mũi Họng, Khám Tổng Quát',
    unit = 'Viên',
    default_dosage = '1 viên / lần x 2 lần/ngày',
    default_instructions = 'Uống ngay sau bữa ăn'
WHERE code = 'MED-CEFU-500';

UPDATE medication_catalog SET
    category = 'Hạ sốt & Giảm đau',
    specialties = 'Khám Tổng Quát, Khám Tai Mũi Họng',
    unit = 'Viên',
    default_dosage = '1 viên / lần / ngày',
    default_instructions = 'Uống sau bữa ăn no'
WHERE code = 'MED-CELE-200';

UPDATE medication_catalog SET
    category = 'Mắt & TMH',
    specialties = 'Khám Tai Mũi Họng, Khám Hô Hấp, Khám Mắt, Khám Tổng Quát',
    unit = 'Viên',
    default_dosage = '1 viên / ngày (buổi tối)',
    default_instructions = 'Uống trước khi đi ngủ'
WHERE code = 'MED-CETI-10';

UPDATE medication_catalog SET
    category = 'Kháng sinh',
    specialties = 'Khám Tiêu Hoá - Gan Mật, Khám Tổng Quát',
    unit = 'Viên',
    default_dosage = '1 viên / lần x 2 lần/ngày',
    default_instructions = 'Uống cách xa bữa ăn có sữa/sắt 2 giờ'
WHERE code = 'MED-CIPRO-500';

UPDATE medication_catalog SET
    category = 'Tim mạch',
    specialties = 'Khám Tim Mạch, Khám Tổng Quát',
    unit = 'Viên',
    default_dosage = '1 viên / ngày',
    default_instructions = 'Uống vào buổi sáng, nuốt nguyên viên'
WHERE code = 'MED-CONCOR-25';

UPDATE medication_catalog SET
    category = 'Tim mạch',
    specialties = 'Khám Tim Mạch, Khám Tổng Quát',
    unit = 'Viên',
    default_dosage = '1 viên / ngày (buổi tối)',
    default_instructions = 'Uống vào buổi tối trước khi đi ngủ'
WHERE code = 'MED-CRESTOR-10';

UPDATE medication_catalog SET
    category = 'Tim mạch',
    specialties = 'Khám Tổng Quát, Khám Tim Mạch',
    unit = 'Viên',
    default_dosage = '1 viên / ngày vào bữa sáng',
    default_instructions = 'Uống nguyên viên cùng bữa ăn sáng'
WHERE code = 'MED-DIAMIC-60';

UPDATE medication_catalog SET
    category = 'Hạ sốt & Giảm đau',
    specialties = 'Khám Tổng Quát',
    unit = 'Viên',
    default_dosage = '1 viên / lần x 2 lần/ngày',
    default_instructions = 'Uống sau bữa ăn no'
WHERE code = 'MED-DICLO-50';

UPDATE medication_catalog SET
    category = 'Tiêu hóa',
    specialties = 'Khám Tiêu Hoá - Gan Mật, Khám Nhi Khoa, Khám Tổng Quát',
    unit = 'Viên',
    default_dosage = '1 viên / lần x 3 lần/ngày',
    default_instructions = 'Uống trước bữa ăn 15-30 phút'
WHERE code = 'MED-DOMPER-10';

UPDATE medication_catalog SET
    category = 'Hạ sốt & Giảm đau',
    specialties = 'Khám Tổng Quát, Khám Hô Hấp, Khám Tai Mũi Họng, Khám Nhi Khoa',
    unit = 'Viên sủi',
    default_dosage = '1 viên sủi / lần khi sốt > 38.5°C',
    default_instructions = 'Hòa tan hoàn toàn trong 150ml nước, cách nhau tối thiểu 4-6 giờ'
WHERE code = 'MED-EFFER-500';

UPDATE medication_catalog SET
    category = 'Tiêu hóa',
    specialties = 'Khám Tiêu Hoá - Gan Mật, Khám Nhi Khoa, Khám Tổng Quát',
    unit = 'Ống',
    default_dosage = '1-2 ống / ngày',
    default_instructions = 'Lắc kỹ trước khi uống, uống cách kháng sinh ít nhất 2 giờ'
WHERE code = 'MED-ENTERO-5ML';

UPDATE medication_catalog SET
    category = 'Tim mạch',
    specialties = 'Khám Tim Mạch, Khám Tổng Quát, Khám Tai Mũi Họng',
    unit = 'Viên',
    default_dosage = '1 viên / lần x 1-2 lần/ngày',
    default_instructions = 'Uống trong bữa ăn'
WHERE code = 'MED-GINKGO-120';

UPDATE medication_catalog SET
    category = 'Tim mạch',
    specialties = 'Khám Tổng Quát, Khám Tim Mạch',
    unit = 'Viên',
    default_dosage = '1 viên / lần x 1-2 lần/ngày',
    default_instructions = 'Uống trong hoặc sau bữa ăn'
WHERE code = 'MED-GLUCO-850';

UPDATE medication_catalog SET
    category = 'Vitamin & Khoáng chất',
    specialties = 'Khám Tổng Quát',
    unit = 'Gói',
    default_dosage = '1 gói / ngày',
    default_instructions = 'Hòa tan vào nước uống trong bữa ăn'
WHERE code = 'MED-GLUC-1500';

UPDATE medication_catalog SET
    category = 'Hạ sốt & Giảm đau',
    specialties = 'Khám Tổng Quát, Khám Hô Hấp, Khám Tai Mũi Họng',
    unit = 'Viên',
    default_dosage = '1 viên / lần khi đau hoặc sốt',
    default_instructions = 'Cách nhau mỗi 4-6 giờ, tối đa 4 viên/ngày'
WHERE code = 'MED-PARA-650';

UPDATE medication_catalog SET
    category = 'Hạ sốt & Giảm đau',
    specialties = 'Khám Tổng Quát, Khám Tai Mũi Họng, Khám Mắt',
    unit = 'Viên',
    default_dosage = '1 viên / lần x 2-3 lần/ngày',
    default_instructions = 'Uống ngay sau bữa ăn no'
WHERE code = 'MED-IBUP-400';

UPDATE medication_catalog SET
    category = 'Kháng sinh',
    specialties = 'Khám Hô Hấp, Khám Tai Mũi Họng, Khám Tiêu Hoá - Gan Mật, Khám Tổng Quát',
    unit = 'Viên',
    default_dosage = '1 viên / lần x 2 lần/ngày',
    default_instructions = 'Uống trước hoặc sau bữa ăn đều được'
WHERE code = 'MED-KLAC-500';

UPDATE medication_catalog SET
    category = 'Kháng sinh',
    specialties = 'Khám Hô Hấp, Khám Tai Mũi Họng, Khám Tổng Quát',
    unit = 'Viên',
    default_dosage = '1 viên / ngày',
    default_instructions = 'Uống với nhiều nước, tránh phơi nắng gắt'
WHERE code = 'MED-LEVO-500';

UPDATE medication_catalog SET
    category = 'Tim mạch',
    specialties = 'Khám Tim Mạch, Khám Tổng Quát',
    unit = 'Viên',
    default_dosage = '1 viên / ngày (buổi tối)',
    default_instructions = 'Uống trước khi đi ngủ'
WHERE code = 'MED-LIPITOR-20';

UPDATE medication_catalog SET
    category = 'Mắt & TMH',
    specialties = 'Khám Tai Mũi Họng, Khám Hô Hấp, Khám Mắt, Khám Tổng Quát',
    unit = 'Viên',
    default_dosage = '1 viên / ngày',
    default_instructions = 'Uống vào buổi sáng hoặc tối'
WHERE code = 'MED-LORA-10';

UPDATE medication_catalog SET
    category = 'Tim mạch',
    specialties = 'Khám Tim Mạch, Khám Tổng Quát',
    unit = 'Viên',
    default_dosage = '1 viên / ngày',
    default_instructions = 'Uống vào một giờ cố định mỗi ngày'
WHERE code = 'MED-LOSA-50';

UPDATE medication_catalog SET
    category = 'Hô hấp',
    specialties = 'Khám Hô Hấp, Khám Tai Mũi Họng, Khám Mắt, Khám Tổng Quát',
    unit = 'Viên',
    default_dosage = '1 viên / ngày (buổi sáng)',
    default_instructions = 'Uống sau bữa ăn sáng no'
WHERE code = 'MED-MEDROL-16';

UPDATE medication_catalog SET
    category = 'Hô hấp',
    specialties = 'Khám Hô Hấp, Khám Tai Mũi Họng, Khám Mắt, Khám Tổng Quát',
    unit = 'Viên',
    default_dosage = '1-2 viên / ngày (buổi sáng)',
    default_instructions = 'Uống sau bữa ăn sáng no'
WHERE code = 'MED-MEDROL-4';

UPDATE medication_catalog SET
    category = 'Hạ sốt & Giảm đau',
    specialties = 'Khám Tổng Quát',
    unit = 'Viên',
    default_dosage = '1 viên / ngày',
    default_instructions = 'Uống sau bữa ăn'
WHERE code = 'MED-MELO-15';

UPDATE medication_catalog SET
    category = 'Tim mạch',
    specialties = 'Khám Tổng Quát, Khám Tim Mạch',
    unit = 'Viên',
    default_dosage = '1 viên / lần x 2 lần/ngày',
    default_instructions = 'Uống trong hoặc ngay sau bữa ăn'
WHERE code = 'MED-METF-500';

UPDATE medication_catalog SET
    category = 'Kháng sinh',
    specialties = 'Khám Tiêu Hoá - Gan Mật, Khám Tai Mũi Họng, Khám Tổng Quát',
    unit = 'Viên',
    default_dosage = '2 viên / lần x 2-3 lần/ngày',
    default_instructions = 'Uống trong hoặc sau bữa ăn, tuyệt đối không dùng rượu bia'
WHERE code = 'MED-METRO-250';

UPDATE medication_catalog SET
    category = 'Tim mạch',
    specialties = 'Khám Tim Mạch, Khám Tổng Quát',
    unit = 'Viên',
    default_dosage = '1 viên / ngày',
    default_instructions = 'Uống vào buổi sáng cố định'
WHERE code = 'MED-MICAR-40';

UPDATE medication_catalog SET
    category = 'Mắt & TMH',
    specialties = 'Khám Tai Mũi Họng, Khám Hô Hấp, Khám Mắt, Khám Nhi Khoa, Khám Tổng Quát',
    unit = 'Chai',
    default_dosage = '1 chai 500ml',
    default_instructions = 'Súc họng miệng hoặc rửa mũi 2-3 lần/ngày'
WHERE code = 'MED-NACL-09';

UPDATE medication_catalog SET
    category = 'Tiêu hóa',
    specialties = 'Khám Tiêu Hoá - Gan Mật, Khám Tổng Quát',
    unit = 'Viên',
    default_dosage = '1 viên / ngày',
    default_instructions = 'Uống trước bữa ăn sáng 30-60 phút'
WHERE code = 'MED-NEXIUM-40';

UPDATE medication_catalog SET
    category = 'Tiêu hóa',
    specialties = 'Khám Tiêu Hoá - Gan Mật, Khám Tổng Quát',
    unit = 'Viên',
    default_dosage = '1 viên / ngày',
    default_instructions = 'Uống trước bữa ăn sáng 30 phút'
WHERE code = 'MED-OMEP-20';

UPDATE medication_catalog SET
    category = 'Mắt & TMH',
    specialties = 'Khám Tai Mũi Họng, Khám Hô Hấp',
    unit = 'Lọ',
    default_dosage = '1 lọ xịt',
    default_instructions = 'Xịt 1-2 nhát mỗi bên mũi, ngày 2-3 lần (dùng không quá 7 ngày)'
WHERE code = 'MED-OTRIVIN-01';

UPDATE medication_catalog SET
    category = 'Tiêu hóa',
    specialties = 'Khám Tiêu Hoá - Gan Mật, Khám Tổng Quát',
    unit = 'Viên',
    default_dosage = '1 viên / ngày',
    default_instructions = 'Uống vào buổi sáng trước bữa ăn 30-60 phút'
WHERE code = 'MED-PANTO-40';

UPDATE medication_catalog SET
    category = 'Hạ sốt & Giảm đau',
    specialties = 'Khám Tổng Quát, Khám Hô Hấp, Khám Tai Mũi Họng, Khám Nhi Khoa',
    unit = 'Viên',
    default_dosage = '1-2 viên / lần khi đau hoặc sốt',
    default_instructions = 'Cách nhau tối thiểu 4-6 giờ, tối đa 4g/ngày'
WHERE code = 'MED-PARA-500';

UPDATE medication_catalog SET
    category = 'Tiêu hóa',
    specialties = 'Khám Tiêu Hoá - Gan Mật, Khám Tổng Quát',
    unit = 'Gói',
    default_dosage = '1 gói / lần x 2-3 lần/ngày',
    default_instructions = 'Uống khi có cơn đau dạ dày hoặc sau bữa ăn'
WHERE code = 'MED-PHOSPHO-P';

UPDATE medication_catalog SET
    category = 'Hô hấp',
    specialties = 'Khám Hô Hấp, Khám Tai Mũi Họng, Khám Mắt, Khám Tổng Quát',
    unit = 'Viên',
    default_dosage = '1-2 viên / ngày (buổi sáng)',
    default_instructions = 'Uống sau bữa ăn sáng'
WHERE code = 'MED-PRED-5';

UPDATE medication_catalog SET
    category = 'Hô hấp',
    specialties = 'Khám Hô Hấp, Khám Nhi Khoa, Khám Tổng Quát',
    unit = 'Viên',
    default_dosage = '1 viên / lần x 2-3 lần/ngày',
    default_instructions = 'Uống sau bữa ăn'
WHERE code = 'MED-SALB-2';

UPDATE medication_catalog SET
    category = 'Tiêu hóa',
    specialties = 'Khám Tiêu Hoá - Gan Mật, Khám Tổng Quát',
    unit = 'Viên',
    default_dosage = '1 viên / lần x 2 lần/ngày',
    default_instructions = 'Uống sau bữa ăn'
WHERE code = 'MED-SILY-140';

UPDATE medication_catalog SET
    category = 'Tiêu hóa',
    specialties = 'Khám Tiêu Hoá - Gan Mật, Khám Nhi Khoa, Khám Tổng Quát',
    unit = 'Gói',
    default_dosage = '1 gói / lần x 2-3 lần/ngày',
    default_instructions = 'Pha vào 50ml nước ấm, uống cách xa bữa ăn'
WHERE code = 'MED-SMECTA-3G';

UPDATE medication_catalog SET
    category = 'Hô hấp',
    specialties = 'Khám Hô Hấp, Khám Tai Mũi Họng, Khám Mắt, Khám Tổng Quát',
    unit = 'Viên',
    default_dosage = '1 viên / ngày',
    default_instructions = 'Uống trước bữa ăn với nước lọc'
WHERE code = 'MED-TELFAST-180';

UPDATE medication_catalog SET
    category = 'Mắt & TMH',
    specialties = 'Khám Mắt, Khám Tai Mũi Họng',
    unit = 'Lọ',
    default_dosage = '1 lọ nhỏ',
    default_instructions = 'Nhỏ 1-2 giọt vào mắt bị bệnh, ngày 3-4 lần'
WHERE code = 'MED-TOBR-03';

UPDATE medication_catalog SET
    category = 'Hạ sốt & Giảm đau',
    specialties = 'Khám Tổng Quát',
    unit = 'Viên',
    default_dosage = '1 viên / lần khi đau',
    default_instructions = 'Uống sau bữa ăn, không quá 4 viên/ngày'
WHERE code = 'MED-ULTRA-375';

UPDATE medication_catalog SET
    category = 'Hô hấp',
    specialties = 'Khám Hô Hấp, Khám Nhi Khoa, Khám Tổng Quát',
    unit = 'Ống hít',
    default_dosage = '1-2 nhát hít / lần khi khó thở',
    default_instructions = 'Lắc đều ống hít, thở ra hết cỡ rồi hít sâu đồng thời ấn xịt, nín thở 10 giây'
WHERE code = 'MED-VENTO-INH';

UPDATE medication_catalog SET
    category = 'Vitamin & Khoáng chất',
    specialties = 'Khám Tổng Quát, Khám Tim Mạch',
    unit = 'Viên',
    default_dosage = '1 viên / lần x 2 lần/ngày',
    default_instructions = 'Uống sau bữa ăn'
WHERE code = 'MED-VIT-3B';

UPDATE medication_catalog SET
    category = 'Vitamin & Khoáng chất',
    specialties = 'Khám Tổng Quát, Khám Hô Hấp, Khám Nhi Khoa, Khám Tai Mũi Họng',
    unit = 'Viên',
    default_dosage = '1 viên / ngày',
    default_instructions = 'Uống vào buổi sáng hoặc trưa sau ăn'
WHERE code = 'MED-VITC-500';
