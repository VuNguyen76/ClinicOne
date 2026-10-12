import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { ActivatedRoute } from '@angular/router';
import { describe, expect, it } from 'vitest';
import { InvoiceLookup } from './invoice-lookup';

describe('InvoiceLookup', () => {
  let fixture: ComponentFixture<InvoiceLookup>;
  let component: InvoiceLookup;
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [InvoiceLookup],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              queryParamMap: {
                get: () => null,
              },
            },
          },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(InvoiceLookup);
    component = fixture.componentInstance;
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    http.expectOne('/api/v1/invoices').flush([]);
    fixture.detectChanges();
  });

  afterEach(() => {
    http.verify();
  });

  it('renders a patient invoice lookup form', () => {
    const text = fixture.nativeElement.textContent;
    expect(text).toContain('Phiếu thanh toán');
    expect(text).toContain('Mã phiếu thanh toán');
    expect(text).toContain('Mã lịch hẹn');
    expect(text).toContain('Tra cứu phiếu thanh toán của tài khoản đang đăng nhập.');
    expect(text).toContain('Phiếu thanh toán');
    expect(text).not.toContain('SIGNATURE VALID');
  });

  it('switches between lookup by invoice code and appointment code', () => {
    component.setLookupType('appointmentCode');
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Mã lịch hẹn');

    component.setLookupType('invoiceCode');
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Mã phiếu thanh toán');
  });

  it('renders only the persisted receipt returned by the backend', () => {
    component.invoiceCodeInput.set('INV-REAL-1');
    component.search();
    http.expectOne('/api/v1/invoices/INV-REAL-1').flush({
      id: 'invoice-1', invoiceCode: 'INV-REAL-1', appointmentCode: 'APT-001',
      patientName: 'Patient A', patientPhone: '0912345678', doctorName: 'Doctor A', specialty: 'Nội tổng quát',
      examFee: 250000, labFee: 0, medicationFee: 0, discountAmount: 0, totalAmount: 250000, paidAmount: 0,
      paymentMethod: null, status: 'PENDING', cashierName: 'staff-1', createdAt: '2026-10-12T02:00:00Z',
      paidAt: null, items: [{ id: 'line-1', itemType: 'CONSULTATION', itemName: 'Khám nội tổng quát',
        unit: 'Lượt', quantity: 1, unitPrice: 250000, totalPrice: 250000, lineNumber: 1 }],
    });
    fixture.detectChanges();

    expect(component.error()).toBe('');
    const text = fixture.nativeElement.textContent;
    expect(text).toContain('INV-REAL-1');
    expect(text).toContain('Patient A');
    expect(text).toContain('250,000 đ');
    expect(text).not.toContain('NGUYỄN THANH VŨ');
    expect(text).not.toContain('SIGNATURE VALID');
  });

  it('loads the authenticated patient receipt list from the backend', () => {
    component.loadReceipts();
    http.expectOne('/api/v1/invoices').flush([{
      id: 'invoice-2', invoiceCode: 'INV-REAL-2', appointmentCode: 'APT-002',
      patientName: 'Patient B', patientPhone: '0912345678', doctorName: 'Doctor B', specialty: 'Tim mạch',
      examFee: 300000, labFee: 0, medicationFee: 0, discountAmount: 0, totalAmount: 300000, paidAmount: 300000,
      paymentMethod: 'CASH', status: 'PAID', cashierName: 'staff-1', createdAt: '2026-10-12T02:00:00Z',
      paidAt: '2026-10-12T02:30:00Z', items: [],
    }]);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('INV-REAL-2');
    expect(fixture.nativeElement.textContent).toContain('Đã thanh toán');
  });

  it('looks up a patient receipt by appointment code using the matching endpoint', () => {
    component.setLookupType('appointmentCode');
    component.appointmentCodeInput.set('APT-003');
    component.search();
    http.expectOne('/api/v1/invoices/appointments/APT-003').flush({
      id: 'invoice-3', invoiceCode: 'INV-REAL-3', appointmentCode: 'APT-003',
      patientName: 'Patient C', patientPhone: null, doctorName: 'Doctor C', specialty: 'Nội tổng quát',
      examFee: 250000, labFee: 0, medicationFee: 0, discountAmount: 0, totalAmount: 250000, paidAmount: 0,
      paymentMethod: null, status: 'PENDING', cashierName: 'staff-1', createdAt: '2026-10-12T02:00:00Z',
      paidAt: null, items: [],
    });
    fixture.detectChanges();

    expect(component.invoiceResult()?.invoiceCode).toBe('INV-REAL-3');
  });
});
