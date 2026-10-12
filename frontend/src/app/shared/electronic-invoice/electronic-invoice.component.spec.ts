import { ComponentFixture, TestBed } from '@angular/core/testing';
import { describe, expect, it } from 'vitest';
import { buildElectronicInvoice } from '../../core/invoice/vietnamese-currency';
import { ElectronicInvoiceComponent } from './electronic-invoice.component';

describe('ElectronicInvoiceComponent', () => {
  let component: ElectronicInvoiceComponent;
  let fixture: ComponentFixture<ElectronicInvoiceComponent>;

  it('renders electronic invoice with correct hospital header and items', () => {
    TestBed.configureTestingModule({
      imports: [ElectronicInvoiceComponent],
    });

    fixture = TestBed.createComponent(ElectronicInvoiceComponent);
    component = fixture.componentInstance;

    const sampleInvoice = buildElectronicInvoice({
      appointmentCode: 'CL-YJTPP2AJYK5G',
      patientName: 'Trần Thị Ngọc Tuyết',
      patientPhone: '0900000001',
      specialty: 'Khám Tổng Quát',
      serviceName: 'Khám chuyên khoa Ung Bướu & Tổng Quát',
      doctorName: 'BS. CKII Nguyễn An',
      prescriptionLines: [
        { medicationName: 'Avegra Biocad', quantity: 1, unit: 'Lọ' },
        { medicationName: 'Gạc tẩm cồn tiệt trùng', quantity: 5, unit: 'Miếng' },
      ],
    });

    fixture.componentRef.setInput('invoice', sampleInvoice);
    fixture.detectChanges();

    const text = fixture.nativeElement.textContent;
    expect(text).toContain('PHÒNG KHÁM ĐA KHOA CLINICONE');
    expect(text).toContain('HÓA ĐƠN BÁN HÀNG');
    expect(text).toContain('TRẦN THỊ NGỌC TUYẾT');
    expect(text).toContain('2K26TVP');
    expect(text).toContain('Khám chuyên khoa Ung Bướu & Tổng Quát');
    expect(text).toContain('Avegra Biocad');
    expect(text).toContain('Gạc tẩm cồn tiệt trùng');
    expect(text).toContain('SIGNATURE VALID');
    expect(text).toContain('KCT');
  });
});
