import { describe, expect, it } from 'vitest';
import { buildElectronicInvoice, readVietnameseCurrency } from './vietnamese-currency';

describe('vietnamese-currency', () => {
  it('reads zero correctly', () => {
    expect(readVietnameseCurrency(0)).toBe('Không đồng chẵn.');
  });

  it('reads hundreds of thousands correctly', () => {
    expect(readVietnameseCurrency(250_000)).toBe('Hai trăm năm mươi nghìn đồng chẵn.');
    expect(readVietnameseCurrency(200_000)).toBe('Hai trăm nghìn đồng chẵn.');
  });

  it('reads millions with complex digits correctly', () => {
    // 7.253.793 (số tiền từ hóa đơn mẫu BV Ung Bướu trên Studocu)
    const result = readVietnameseCurrency(7_253_793);
    expect(result).toContain('Bảy triệu');
    expect(result).toContain('hai trăm năm mươi ba nghìn');
    expect(result).toContain('bảy trăm chín mươi ba đồng chẵn.');
  });

  it('builds an electronic invoice accurately from clinical data', () => {
    const invoice = buildElectronicInvoice({
      appointmentCode: 'CL-YJTPP2AJYK5G',
      patientName: 'Nguyễn Thanh Vũ',
      patientPhone: '0900000001',
      specialty: 'Khám Tổng Quát',
      serviceName: 'Khám tổng quát ClinicOne',
      doctorName: 'BS. CKII Nguyễn An',
      prescriptionLines: [
        { medicationName: 'Acetylcystein 200mg', quantity: 2, unit: 'Gói' },
        { medicationName: 'Paracetamol 500mg', quantity: 10, unit: 'Viên' },
      ],
    });

    expect(invoice.invoiceSerial).toBe('2K26TVP');
    expect(invoice.templateCode).toBe('1/001');
    expect(invoice.vatRate).toBe('KCT');
    expect(invoice.vatAmount).toBe(0);
    expect(invoice.items.length).toBe(3); // 1 service + 2 medications
    expect(invoice.items[0].name).toBe('Khám tổng quát ClinicOne');
    expect(invoice.items[0].amount).toBe(200_000);
    expect(invoice.total).toBeGreaterThan(200_000);
    expect(invoice.amountInWords).toContain('đồng chẵn.');
    expect(invoice.lookupCode).toMatch(/^22005\d{10}$/);
  });
});
