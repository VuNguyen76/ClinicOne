import { TemplatePrescriptionLine } from './medical-record-template-content';

export interface PrescriptionProtocol {
  id: string;
  code: string;
  name: string;
  diagnosisCode: string;
  category: string;
  specialties: string[];
  indication: string;
  treatmentDays: number;
  lines: TemplatePrescriptionLine[];
}

export const PROTOCOL_CATEGORIES = [
  'Hô hấp & Cảm cúm',
  'Tiêu hóa & Dạ dày',
  'Tim mạch & Huyết áp',
  'Cơ xương khớp',
  'Dị ứng & Da liễu',
  'Tất cả',
] as const;

export const CLINICAL_PRESCRIPTION_PROTOCOLS: PrescriptionProtocol[] = [
  {
    id: 'proto-hh-01',
    code: 'PROTO-HH-01',
    name: 'Toa thuốc Viêm họng & Amidan cấp tính',
    diagnosisCode: 'J02 / J03 - Viêm họng & Viêm amidan cấp',
    category: 'Hô hấp & Cảm cúm',
    specialties: ['Khám Tổng Quát', 'Khám Hô Hấp', 'Khám Tai Mũi Họng'],
    indication: 'Đau rát họng, nuốt đau, sốt nhẹ đến vừa, niêm mạc họng đỏ xung huyết.',
    treatmentDays: 7,
    lines: [
      {
        medicationName: 'Amoxicillin 500mg (Kháng sinh nhóm Penicillin)',
        dosage: '1 viên / lần x 2 lần/ngày',
        quantity: 14,
        unit: 'Viên',
        instructions: 'Uống sau bữa ăn sáng và tối, uống nhiều nước',
      },
      {
        medicationName: 'Paracetamol 500mg (Hạ sốt & Giảm đau)',
        dosage: '1 viên khi sốt trên 38.5°C hoặc đau rát họng nhiều',
        quantity: 10,
        unit: 'Viên',
        instructions: 'Uống sau ăn, cách nhau tối thiểu 4 - 6 giờ, tối đa 4 viên/ngày',
      },
      {
        medicationName: 'Alpha Chymotrypsin 4.2mg (Kháng viêm, giảm phù nề)',
        dosage: '2 viên / lần x 2 lần/ngày',
        quantity: 20,
        unit: 'Viên',
        instructions: 'Uống hoặc ngậm dưới lưỡi sau khi ăn',
      },
      {
        medicationName: 'Vitamin C 500mg (Tăng cường đề kháng)',
        dosage: '1 viên / lần x 1 lần/ngày',
        quantity: 7,
        unit: 'Viên',
        instructions: 'Uống sau bữa ăn sáng, không uống lúc đói hoặc ban đêm',
      },
    ],
  },
  {
    id: 'proto-hh-02',
    code: 'PROTO-HH-02',
    name: 'Toa thuốc Cảm cúm & Sốt siêu vi (Có ho đờm)',
    diagnosisCode: 'J00 / J10 - Cảm lạnh thông thường / Cúm mùa',
    category: 'Hô hấp & Cảm cúm',
    specialties: ['Khám Tổng Quát', 'Khám Hô Hấp', 'Khám Nhi Khoa'],
    indication: 'Sốt, nhức đầu mỏi người, ho có đờm, hắt hơi nghẹt mũi, chảy nước mũi trong.',
    treatmentDays: 5,
    lines: [
      {
        medicationName: 'Paracetamol 500mg (Hạ sốt & Giảm đau)',
        dosage: '1 viên / lần x 2-3 lần/ngày khi sốt',
        quantity: 10,
        unit: 'Viên',
        instructions: 'Uống sau khi ăn no, cách nhau ít nhất 4-6 giờ',
      },
      {
        medicationName: 'Acetylcystein 200mg (Long đờm, tiêu nhầy đường hô hấp)',
        dosage: '1 gói / lần x 2 lần/ngày',
        quantity: 10,
        unit: 'Gói',
        instructions: 'Pha với 1 cốc nước ấm, uống sau bữa ăn sáng và chiều',
      },
      {
        medicationName: 'Cetirizine 10mg (Chống dị ứng, giảm nghẹt hắt hơi)',
        dosage: '1 viên / lần / ngày',
        quantity: 5,
        unit: 'Viên',
        instructions: 'Uống vào buổi tối trước khi đi ngủ',
      },
      {
        medicationName: 'Vitamin C 500mg (Tăng cường đề kháng)',
        dosage: '1 viên / lần / ngày',
        quantity: 5,
        unit: 'Viên',
        instructions: 'Uống vào buổi sáng sau ăn no',
      },
    ],
  },
  {
    id: 'proto-hh-03',
    code: 'PROTO-HH-03',
    name: 'Toa thuốc Viêm phế quản cấp tính',
    diagnosisCode: 'J20 - Viêm phế quản cấp',
    category: 'Hô hấp & Cảm cúm',
    specialties: ['Khám Tổng Quát', 'Khám Hô Hấp'],
    indication: 'Ho khan chuyển sang ho có đờm vàng đục, đau tức nhẹ ngực khi ho, sốt nhẹ.',
    treatmentDays: 7,
    lines: [
      {
        medicationName: 'Augmentin 1g / Amoxicillin + Acid Clavulanic 1000mg (Kháng sinh phổ rộng)',
        dosage: '1 viên / lần x 2 lần/ngày',
        quantity: 14,
        unit: 'Viên',
        instructions: 'Uống ngay đầu bữa ăn sáng và tối để hạn chế khó chịu dạ dày',
      },
      {
        medicationName: 'Ambroxol 30mg / Mucosolvan (Tiêu đờm, giảm ho có đờm)',
        dosage: '1 viên / lần x 3 lần/ngày',
        quantity: 21,
        unit: 'Viên',
        instructions: 'Uống với nhiều nước sau mỗi bữa ăn',
      },
      {
        medicationName: 'Paracetamol 500mg (Hạ sốt & Giảm đau)',
        dosage: '1 viên khi sốt hoặc đau đầu',
        quantity: 10,
        unit: 'Viên',
        instructions: 'Uống sau ăn no, cách nhau tối thiểu 4-6 giờ',
      },
    ],
  },
  {
    id: 'proto-th-01',
    code: 'PROTO-TH-01',
    name: 'Toa thuốc Viêm dạ dày cấp & Trào ngược dạ dày (GERD)',
    diagnosisCode: 'K21 / K29 - Trào ngược dạ dày thực quản / Viêm dạ dày',
    category: 'Tiêu hóa & Dạ dày',
    specialties: ['Khám Tổng Quát', 'Khám Tiêu Hoá - Gan Mật'],
    indication: 'Đau tức thượng vị, ợ chua, ợ hơi, nóng rát sau xương ức, đầy bụng khó tiêu.',
    treatmentDays: 14,
    lines: [
      {
        medicationName: 'Omeprazole 20mg (Ức chế bơm Proton giảm tiết acid dạ dày)',
        dosage: '1 viên / lần x 2 lần/ngày',
        quantity: 28,
        unit: 'Viên',
        instructions: 'Uống trước bữa ăn sáng và tối 30 phút, nuốt nguyên viên',
      },
      {
        medicationName: 'Phosphalugel / Nhôm Phosphate gel (Kháng acid, bao phủ niêm mạc)',
        dosage: '1 gói / lần x 2-3 lần/ngày',
        quantity: 20,
        unit: 'Gói',
        instructions: 'Uống khi có cơn đau rát hoặc sau ăn 1-2 giờ',
      },
      {
        medicationName: 'Domperidone 10mg (Điều hòa nhu động, chống nôn trào ngược)',
        dosage: '1 viên / lần x 2 lần/ngày',
        quantity: 20,
        unit: 'Viên',
        instructions: 'Uống trước các bữa ăn 15-30 phút',
      },
    ],
  },
  {
    id: 'proto-th-02',
    code: 'PROTO-TH-02',
    name: 'Toa thuốc Rối loạn tiêu hóa & Tiêu chảy cấp không sốt',
    diagnosisCode: 'K59.1 / A09 - Tiêu chảy & Rối loạn chức năng đường ruột',
    category: 'Tiêu hóa & Dạ dày',
    specialties: ['Khám Tổng Quát', 'Khám Tiêu Hoá - Gan Mật', 'Khám Nhi Khoa'],
    indication: 'Đi ngoài phân lỏng nhiều lần, đau quặn bụng từng cơn, không sốt cao.',
    treatmentDays: 5,
    lines: [
      {
        medicationName: 'Smecta / Diosmectite 3g (Bảo vệ niêm mạc đường tiêu hóa)',
        dosage: '1 gói / lần x 3 lần/ngày',
        quantity: 15,
        unit: 'Gói',
        instructions: 'Pha với 50ml nước lọc, uống giữa các bữa ăn',
      },
      {
        medicationName: 'Oresol 245 (Bù nước và điện giải)',
        dosage: 'Pha 1 gói với đúng 200ml nước đun sôi để nguội',
        quantity: 10,
        unit: 'Gói',
        instructions: 'Uống rải rác trong ngày sau mỗi lần đi ngoài phân lỏng',
      },
      {
        medicationName: 'Men vi sinh Probiotics (Cân bằng hệ vi sinh đường ruột)',
        dosage: '1 gói / lần x 2 lần/ngày',
        quantity: 10,
        unit: 'Gói',
        instructions: 'Uống trực tiếp hoặc pha nước nguội sau ăn 30 phút',
      },
    ],
  },
  {
    id: 'proto-tm-01',
    code: 'PROTO-TM-01',
    name: 'Toa thuốc Tăng huyết áp nguyên phát độ 1 (Khởi đầu)',
    diagnosisCode: 'I10 - Tăng huyết áp vô căn (nguyên phát)',
    category: 'Tim mạch & Huyết áp',
    specialties: ['Khám Tổng Quát', 'Khám Tim Mạch'],
    indication: 'Huyết áp tâm thu 140-159 mmHg hoặc tâm trương 90-99 mmHg, chưa có biến chứng tim mạch.',
    treatmentDays: 30,
    lines: [
      {
        medicationName: 'Amlodipine 5mg (Hạ huyết áp chẹn kênh Canxi)',
        dosage: '1 viên / lần / ngày',
        quantity: 30,
        unit: 'Viên',
        instructions: 'Uống cố định vào buổi sáng hàng ngày sau ăn, đo huyết áp định kỳ',
      },
      {
        medicationName: 'Aspirin 81mg (Chống kết tập tiểu cầu, phòng ngừa huyết khối)',
        dosage: '1 viên / lần / ngày',
        quantity: 30,
        unit: 'Viên',
        instructions: 'Uống ngay sau bữa ăn trưa hoặc tối no, nuốt nguyên viên',
      },
    ],
  },
  {
    id: 'proto-ck-01',
    code: 'PROTO-CK-01',
    name: 'Toa thuốc Thoái hóa khớp & Đau nhức cơ xương khớp',
    diagnosisCode: 'M15 / M19 - Thoái hóa đa khớp / Thoái hóa khớp khác',
    category: 'Cơ xương khớp',
    specialties: ['Khám Tổng Quát'],
    indication: 'Đau nhức khớp gối, cột sống thắt lưng, cứng khớp buổi sáng, vận động khó khăn.',
    treatmentDays: 14,
    lines: [
      {
        medicationName: 'Meloxicam 7.5mg (Kháng viêm không steroid NSAID)',
        dosage: '1 viên / lần / ngày',
        quantity: 14,
        unit: 'Viên',
        instructions: 'Uống ngay sau bữa ăn trưa hoặc tối no với nhiều nước',
      },
      {
        medicationName: 'Omeprazole 20mg (Bảo vệ dạ dày khi dùng NSAID)',
        dosage: '1 viên / lần / ngày',
        quantity: 14,
        unit: 'Viên',
        instructions: 'Uống trước bữa ăn sáng 30 phút',
      },
      {
        medicationName: 'Paracetamol 500mg (Hạ sốt & Giảm đau)',
        dosage: '1 viên khi đau nhiều',
        quantity: 10,
        unit: 'Viên',
        instructions: 'Uống cách liều NSAID ít nhất 4 giờ',
      },
    ],
  },
  {
    id: 'proto-dl-01',
    code: 'PROTO-DL-01',
    name: 'Toa thuốc Dị ứng thời tiết & Mề đay cấp tính',
    diagnosisCode: 'L50 - Mày đay (Mề đay cấp)',
    category: 'Dị ứng & Da liễu',
    specialties: ['Khám Tổng Quát'],
    indication: 'Ngứa ngáy, nổi sẩn phù đỏ rải rác toàn thân sau khi ăn đồ lạ hoặc thay đổi thời tiết.',
    treatmentDays: 7,
    lines: [
      {
        medicationName: 'Cetirizine 10mg (Kháng histamine H1 thế hệ 2)',
        dosage: '1 viên / lần / ngày',
        quantity: 7,
        unit: 'Viên',
        instructions: 'Uống vào buổi tối trước khi đi ngủ',
      },
      {
        medicationName: 'Vitamin C 500mg (Tăng tính bền thành mạch, giảm phù)',
        dosage: '1 viên / lần / ngày',
        quantity: 7,
        unit: 'Viên',
        instructions: 'Uống sau bữa ăn sáng',
      },
    ],
  },
];

export function getProtocolsBySpecialty(specialty?: string): PrescriptionProtocol[] {
  if (!specialty || !specialty.trim()) return CLINICAL_PRESCRIPTION_PROTOCOLS;
  const specLower = specialty.toLowerCase().trim();
  const isGeneral = specLower.includes('tổng quát') || specLower.includes('nội') || specLower.includes('đa khoa');
  return CLINICAL_PRESCRIPTION_PROTOCOLS.filter((p) =>
    (isGeneral && p.specialties.some((s) => s.toLowerCase().includes('tổng quát'))) ||
    p.specialties.some((s) => s.toLowerCase().includes(specLower) || specLower.includes(s.toLowerCase()))
  );
}

export function searchProtocols(
  query: string,
  category?: string,
  specialty?: string
): PrescriptionProtocol[] {
  let list = CLINICAL_PRESCRIPTION_PROTOCOLS;
  if (category && category !== 'Tất cả') {
    list = list.filter((p) => p.category === category);
  }
  const q = query.trim().toLowerCase();
  if (!q) return list;
  return list.filter(
    (p) =>
      p.name.toLowerCase().includes(q) ||
      p.code.toLowerCase().includes(q) ||
      p.diagnosisCode.toLowerCase().includes(q) ||
      p.indication.toLowerCase().includes(q) ||
      p.lines.some((l) => l.medicationName.toLowerCase().includes(q))
  );
}
