import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { ActivatedRoute } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { InvoiceLookup } from './invoice-lookup';

describe('InvoiceLookup', () => {
  let fixture: ComponentFixture<InvoiceLookup>;
  let component: InvoiceLookup;
  let http: HttpTestingController;

  beforeEach(async () => {
    sessionStorage.setItem('clinicOneSessionType', 'PATIENT');
    sessionStorage.setItem('clinicOneAccessToken', 'test-token');
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
    sessionStorage.clear();
  });

  it('renders a patient invoice lookup form', () => {
    const text = fixture.nativeElement.textContent;
    expect(text).toContain('Phiếu TT');
    expect(text).toContain('Mã phiếu');
    expect(text).toContain('Mã lịch hẹn');
    expect(text).toContain('Tra cứu bằng mã phiếu.');
    expect(text).not.toContain('Chỉ hiển thị phiếu');
    expect(text).not.toContain('SIGNATURE VALID');
  });

  it('looks up a public receipt by code', () => {
    component.invoiceCodeInput.set('INV-0123456789ABCDEF0123456789ABCDEF');
    component.search();
    http.expectOne({ method: 'POST', url: '/api/v1/public/invoices/lookup' }).flush({
      invoiceCode: 'INV-0123456789ABCDEF0123456789ABCDEF', patientName: 'Patient A',
      doctorName: 'Doctor A', specialty: 'Nội tổng quát', totalAmount: 250000, paidAmount: 250000,
      status: 'PAID', createdAt: '2026-10-12T02:00:00Z', items: [],
    });
    fixture.detectChanges();

    expect(component.invoiceResult()?.invoiceCode).toBe('INV-0123456789ABCDEF0123456789ABCDEF');
  });

  it('does not load a private invoice list for anonymous visitors', () => {
    fixture.destroy();
    sessionStorage.clear();
    fixture = TestBed.createComponent(InvoiceLookup);
    component = fixture.componentInstance;
    fixture.detectChanges();

    expect(http.match('/api/v1/invoices')).toHaveLength(0);
    expect(fixture.nativeElement.textContent).not.toContain('Mã lịch hẹn');
    expect(fixture.nativeElement.textContent).not.toContain('Phiếu TT của tôi');
  });

  it('switches between lookup by invoice code and appointment code', () => {
    component.setLookupType('appointmentCode');
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Mã lịch hẹn');

    component.setLookupType('invoiceCode');
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Mã phiếu');
  });

  it('renders only the persisted receipt returned by the backend', () => {
    component.invoiceCodeInput.set('INV-0123456789ABCDEF0123456789ABCDEF');
    component.search();
    http.expectOne({ method: 'POST', url: '/api/v1/public/invoices/lookup' }).flush({
      invoiceCode: 'INV-0123456789ABCDEF0123456789ABCDEF',
      patientName: 'Patient A', doctorName: 'Doctor A', specialty: 'Nội tổng quát',
      totalAmount: 250000, paidAmount: 0, status: 'PENDING', createdAt: '2026-10-12T02:00:00Z',
      items: [{ itemName: 'Khám nội tổng quát', unit: 'Lượt', quantity: 1,
        unitPrice: 250000, totalPrice: 250000 }],
    });
    fixture.detectChanges();

    expect(component.error()).toBe('');
    const text = fixture.nativeElement.textContent;
    expect(text).toContain('INV-0123456789ABCDEF0123456789ABCDEF');
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
