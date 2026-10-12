/**
 * Tiện ích chuyển đổi số tiền thành chữ bằng tiếng Việt theo chuẩn kế toán & hóa đơn điện tử.
 * Ví dụ: 7.253.793 -> "Bảy triệu hai trăm năm mươi ba nghìn bảy trăm chín mươi ba đồng chẵn."
 */
const DIGITS = ['không', 'một', 'hai', 'ba', 'bốn', 'năm', 'sáu', 'bảy', 'tám', 'chín'];

function readThreeDigits(n: number, showZeroHundred: boolean): string {
  const hundred = Math.floor(n / 100);
  const remainder = n % 100;
  const ten = Math.floor(remainder / 10);
  const unit = remainder % 10;
  const words: string[] = [];

  if (hundred > 0 || showZeroHundred) {
    words.push(`${DIGITS[hundred]} trăm`);
  }

  if (ten > 1) {
    words.push(`${DIGITS[ten]} mươi`);
    if (unit === 1) {
      words.push('mốt');
    } else if (unit === 4) {
      words.push('tư');
    } else if (unit === 5) {
      words.push('lăm');
    } else if (unit > 0) {
      words.push(DIGITS[unit]);
    }
  } else if (ten === 1) {
    words.push('mười');
    if (unit === 1) {
      words.push('một');
    } else if (unit === 5) {
      words.push('lăm');
    } else if (unit > 0) {
      words.push(DIGITS[unit]);
    }
  } else if (hundred > 0 || showZeroHundred) {
    if (unit > 0) {
      words.push(`lẻ ${DIGITS[unit]}`);
    }
  } else if (unit > 0) {
    words.push(DIGITS[unit]);
  }

  return words.join(' ');
}

export function readVietnameseCurrency(amount: number): string {
  if (amount === 0) {
    return 'Không đồng chẵn.';
  }

  const scales = ['', 'nghìn', 'triệu', 'tỷ', 'nghìn tỷ', 'triệu tỷ'];
  const integerPart = Math.floor(Math.abs(amount));
  const groups: number[] = [];

  let temp = integerPart;
  while (temp > 0) {
    groups.push(temp % 1000);
    temp = Math.floor(temp / 1000);
  }

  const parts: string[] = [];
  for (let i = groups.length - 1; i >= 0; i--) {
    const group = groups[i];
    if (group === 0) continue;
    const showZeroHundred = i < groups.length - 1;
    const groupText = readThreeDigits(group, showZeroHundred);
    const scale = scales[i];
    parts.push(scale ? `${groupText} ${scale}` : groupText);
  }

  let result = parts.join(' ').replace(/\s+/g, ' ').trim();
  if (result) {
    result = result.charAt(0).toUpperCase() + result.slice(1) + ' đồng chẵn.';
  } else {
    result = 'Không đồng chẵn.';
  }

  return result;
}

export interface InvoiceItem {
  stt: number;
  name: string;
  unit: string;
  quantity: number;
  unitPrice: number;
  amount: number;
  category: 'SERVICE' | 'MEDICATION' | 'MATERIAL';
}

export interface ElectronicInvoiceData {
  invoiceNumber: string;
  invoiceSerial: string;
  templateCode: string;
  lookupCode: string;
  lookupUrl: string;
  cqtCode: string;
  issueDate: string; // YYYY-MM-DD
  patientName: string;
  patientPhone: string;
  patientDateOfBirth?: string;
  patientGender?: string;
  patientAddress?: string;
  appointmentCode: string;
  doctorName: string;
  specialty: string;
  paymentMethod: string;
  items: InvoiceItem[];
  subtotal: number;
  vatRate: string; // KCT
  vatAmount: number;
  total: number;
  amountInWords: string;
}

/**
 * Bảng giá dịch vụ khám bệnh và đơn giá thuốc chuẩn định mức
 */
const DEFAULT_SERVICE_PRICE: Record<string, number> = {
  'Khám Tổng Quát': 200_000,
  'Khám Tim Mạch': 250_000,
  'Khám Hô Hấp': 220_000,
  'Khám Tiêu Hoá - Gan Mật': 250_000,
  'Khám Nhi Khoa': 200_000,
  'Khám Tai Mũi Họng': 220_000,
  'Khám Mắt': 200_000,
};

function estimateMedicationPrice(name: string, unit: string): number {
  const lower = name.toLowerCase();
  if (lower.includes('avegra') || lower.includes('sinh học') || lower.includes('hóa trị')) return 2_450_000;
  if (lower.includes('kháng sinh') || lower.includes('cefuroxime') || lower.includes('amoxicillin') || lower.includes('augmentin')) return 12_000;
  if (lower.includes('acetylcystein') || lower.includes('long đờm')) return 3_500;
  if (lower.includes('paracetamol') || lower.includes('panadol') || lower.includes('hạ sốt')) return 1_500;
  if (lower.includes('loratadine') || lower.includes('dị ứng')) return 2_500;
  if (lower.includes('vitamin')) return 2_000;
  if (lower.includes('gạc') || lower.includes('bơm tiêm') || lower.includes('kim tiêm')) return 5_000;
  if (unit.toLowerCase().includes('lọ') || unit.toLowerCase().includes('chai')) return 45_000;
  if (unit.toLowerCase().includes('gói')) return 4_000;
  return 3_000;
}

export function buildElectronicInvoice(params: {
  appointmentCode?: string | null;
  patientName?: string | null;
  patientPhone?: string | null;
  patientDateOfBirth?: string | null;
  patientGender?: string | null;
  patientAddress?: string | null;
  doctorName?: string | null;
  specialty?: string | null;
  serviceName?: string | null;
  signedAt?: string | null;
  appointmentDate?: string | null;
  prescriptionLines?: Array<{
    medicationName: string;
    quantity: number;
    unit?: string | null;
    dosage?: string | null;
  }> | null;
}): ElectronicInvoiceData {
  const specialty = params.specialty?.trim() || 'Khám Tổng Quát';
  const serviceName = params.serviceName?.trim() || `Khám chuyên khoa ${specialty}`;
  const servicePrice = DEFAULT_SERVICE_PRICE[specialty] || 200_000;

  const items: InvoiceItem[] = [];
  let stt = 1;

  // Dịch vụ 1: Tiền công khám bệnh
  items.push({
    stt: stt++,
    name: serviceName,
    unit: 'Lượt',
    quantity: 1,
    unitPrice: servicePrice,
    amount: servicePrice,
    category: 'SERVICE',
  });

  // Dịch vụ 2+: Thuốc theo đơn
  if (params.prescriptionLines && params.prescriptionLines.length > 0) {
    params.prescriptionLines.forEach((line) => {
      const unit = line.unit?.trim() || 'Viên';
      const qty = Math.max(1, Number(line.quantity) || 1);
      const unitPrice = estimateMedicationPrice(line.medicationName, unit);
      const amount = qty * unitPrice;
      items.push({
        stt: stt++,
        name: line.medicationName,
        unit,
        quantity: qty,
        unitPrice,
        amount,
        category: 'MEDICATION',
      });
    });
  }

  const subtotal = items.reduce((sum, item) => sum + item.amount, 0);
  const vatRate = 'KCT'; // Không chịu thuế theo quy định dịch vụ y tế
  const vatAmount = 0;
  const total = subtotal + vatAmount;

  // Sinh mã tra cứu 15 chữ số duy nhất (mẫu Bệnh viện Ung Bướu / EasyInvoice)
  const codeSeed = (params.appointmentCode || 'CLINICONE').replace(/[^a-zA-Z0-9]/g, '');
  let hashNum = 0;
  for (let i = 0; i < codeSeed.length; i++) {
    hashNum = (hashNum * 31 + codeSeed.charCodeAt(i)) >>> 0;
  }
  const lookupCode = `22005${String(hashNum).padStart(10, '0').slice(-10)}`;

  // Sinh số hóa đơn 7 chữ số (ví dụ: 0001896)
  const invoiceNum = String((hashNum % 900000) + 100000).padStart(7, '0');

  const issueDate = params.signedAt
    ? params.signedAt.slice(0, 10)
    : params.appointmentDate || new Date().toISOString().slice(0, 10);

  return {
    invoiceNumber: invoiceNum,
    invoiceSerial: '2K26TVP',
    templateCode: '1/001',
    lookupCode,
    lookupUrl: 'https://hoadon.clinicone.vn',
    cqtCode: `00E26-${invoiceNum}`,
    issueDate,
    patientName: (params.patientName?.trim() || 'NGUYỄN THANH VŨ').toUpperCase(),
    patientPhone: params.patientPhone?.trim() || '0900000001',
    patientDateOfBirth: params.patientDateOfBirth?.trim() || '2000-01-01',
    patientGender: params.patientGender?.trim() || 'Nam',
    patientAddress: params.patientAddress?.trim() || '123 Nguyễn Văn Cừ, Phường 4, Quận 5, TP. Hồ Chí Minh',
    appointmentCode: params.appointmentCode?.trim() || 'CL-YJTPP2AJYK5G',
    doctorName: params.doctorName?.trim() || 'BS. CKII Nguyễn An',
    specialty,
    paymentMethod: 'Tiền mặt / Chuyển khoản (TM/CK)',
    items,
    subtotal,
    vatRate,
    vatAmount,
    total,
    amountInWords: readVietnameseCurrency(total),
  };
}
