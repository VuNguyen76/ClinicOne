import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { describe, expect, it } from 'vitest';
import { ReceptionPayments } from './reception-payments';

describe('ReceptionPayments', () => {
  let fixture: ComponentFixture<ReceptionPayments>;
  let component: any;
  let http: HttpTestingController;

  beforeEach(async () => {
    sessionStorage.setItem('clinicOneStaffRole', 'RECEPTIONIST');
    sessionStorage.setItem('clinicOneStaffRoles', JSON.stringify(['RECEPTIONIST']));
    await TestBed.configureTestingModule({
      imports: [ReceptionPayments],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(ReceptionPayments);
    component = fixture.componentInstance as any;
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  it('uses one page title without the redundant receipt heading or icon', () => {
    fixture.detectChanges();
    const text = fixture.nativeElement.textContent as string;

    expect(fixture.nativeElement.querySelectorAll('h1')).toHaveLength(1);
    expect(text).toContain('Thu phí khám bệnh');
    expect(text).not.toContain('Tra cứu lịch để lập phiếu thu');
    expect(text).not.toContain('Phiếu thu nội bộ');
    expect(text).not.toContain('receipt_long');
    expect(text).not.toContain('Tiếp nhận · Thanh toán');
  });

  it('matches the compact reception profile search field sizing', () => {
    const searchCard = fixture.nativeElement.querySelector('[data-testid="payment-search-card"]') as HTMLElement;
    const appointmentCode = fixture.nativeElement.querySelector('input[name="appointmentCode"]') as HTMLInputElement;
    const submit = searchCard.querySelector('button[type="submit"]') as HTMLButtonElement;

    expect(searchCard.classList.contains('erp-filter-bar')).toBe(true);
    expect(appointmentCode.classList.contains('erp-input')).toBe(true);
    expect(appointmentCode.classList.contains('h-11')).toBe(false);
    expect(appointmentCode.classList.contains('text-base')).toBe(false);
    expect(submit.classList.contains('erp-btn-primary')).toBe(true);
    expect(submit.classList.contains('h-11')).toBe(false);
  });

  it('requires an appointment code before creating a receipt', () => {
    component.createReceipt();
    expect(component.error()).toContain('mã lịch hẹn');
    http.expectNone('/api/v1/invoices/appointments/');
  });

  it('preserves a new appointment code typed while the previous lookup is pending', () => {
    component.appointmentCode.set('APT-001');
    component.createReceipt();
    const lookup = http.expectOne({ method: 'POST', url: '/api/v1/invoices/appointments/APT-001' });

    component.appointmentCode.set('APT-002');
    lookup.flush(receipt());

    expect(component.appointmentCode()).toBe('APT-002');
    expect(component.receipt()?.appointmentCode).toBe('APT-001');
  });

  it('does not send duplicate receipt lookups while the first lookup is pending', () => {
    component.appointmentCode.set('APT-001');
    component.createReceipt();
    component.createReceipt();

    const requests = http.match('/api/v1/invoices/appointments/APT-001');
    expect(requests).toHaveLength(1);
    requests[0].flush(receipt());
  });

  it('shows the server business reason when a receipt is not eligible yet', () => {
    component.appointmentCode.set('APT-001');
    component.createReceipt();
    http.expectOne({ method: 'POST', url: '/api/v1/invoices/appointments/APT-001' }).flush(
      { code: 'SIGNED_RECORD_REQUIRED', message: 'Lượt khám chưa có phiếu khám đã ký.' },
      { status: 409, statusText: 'Conflict' },
    );
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[role="alert"]')?.textContent).toContain('Lượt khám chưa có phiếu khám đã ký.');
  });

  it('creates a server-priced receipt and records payment through the API', () => {
    component.appointmentCode.set('APT-001');
    component.createReceipt();
    http.expectOne({ method: 'POST', url: '/api/v1/invoices/appointments/APT-001' }).flush(receipt());
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('250.000');
    expect(fixture.nativeElement.textContent).toContain('Patient A');
    expect(fixture.nativeElement.textContent).not.toContain('SIGNATURE VALID');

    component.paymentMethod.set('CASH');
    component.markPaid();
    http.expectOne({ method: 'POST', url: '/api/v1/invoices/INV-001/payment' }).flush({
      ...receipt(), status: 'PAID', paidAmount: 250000, paymentMethod: 'CASH',
    });
    fixture.detectChanges();

    expect(component.receipt()?.status).toBe('PAID');
    expect(fixture.nativeElement.textContent).toContain('Đã thanh toán');
  });

  it('explains that reception records the full invoice amount, not a partial payment', () => {
    component.appointmentCode.set('APT-001');
    component.createReceipt();
    http.expectOne({ method: 'POST', url: '/api/v1/invoices/appointments/APT-001' }).flush(receipt());
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('không hỗ trợ ghi nhận thanh toán một phần');
    expect(fixture.nativeElement.textContent).toContain('Xác nhận đã thu 250.000 đ');
  });

  it('does not submit the same payment twice while the first request is pending', () => {
    component.appointmentCode.set('APT-001');
    component.createReceipt();
    http.expectOne({ method: 'POST', url: '/api/v1/invoices/appointments/APT-001' }).flush(receipt());

    component.markPaid();
    component.markPaid();

    const paymentRequests = http.match('/api/v1/invoices/INV-001/payment');
    expect(paymentRequests).toHaveLength(1);
    paymentRequests[0].flush({ ...receipt(), status: 'PAID', paidAmount: 250000, paymentMethod: 'CASH' });
  });
});

function receipt() {
  return {
    id: 'invoice-1', invoiceCode: 'INV-001', appointmentCode: 'APT-001',
    patientName: 'Patient A', patientPhone: '0912345678', doctorName: 'Doctor A', specialty: 'Nội tổng quát',
    examFee: 250000, labFee: 0, medicationFee: 0, discountAmount: 0, totalAmount: 250000, paidAmount: 0,
    paymentMethod: null, status: 'PENDING', cashierName: 'staff-1', createdAt: '2026-10-12T02:00:00Z',
    paidAt: null, items: [{ id: 'line-1', itemType: 'CONSULTATION', itemName: 'Khám nội tổng quát',
      unit: 'Lượt', quantity: 1, unitPrice: 250000, totalPrice: 250000, lineNumber: 1 }],
  };
}
