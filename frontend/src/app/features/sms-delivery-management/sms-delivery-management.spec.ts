import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { SmsDeliveryManagement } from './sms-delivery-management';

describe('SmsDeliveryManagement', () => {
  let fixture: ComponentFixture<SmsDeliveryManagement>;
  let http: HttpTestingController;

  const mockSmsItems = [
    {
      id: 'sms-001-uuid',
      eventKey: 'APPOINTMENT_LATE_WARNING:0b75cfa2-0c4c-4039-9541-9c30c3a2fbe9',
      phone: '0912345678',
      status: 'SENT',
      attempts: 1,
      availableAt: '2026-08-20T08:00:00Z',
      sentAt: '2026-08-20T08:01:00Z',
      lastError: null,
      createdAt: '2026-08-20T08:00:00Z',
      message: 'ClinicOne: Quy khach co lich hen LH-20260820-001 luc 08:30 hom nay nhung chua den tiep don. Vui long lien he 19000000 neu can ho tro.',
    },
    {
      id: 'sms-002-uuid',
      eventKey: 'APPOINTMENT_REMINDER_24H:7f8e9d0a-1b2c-3d4e-5f6a-7b8c9d0e1f2a',
      phone: '0987654321',
      status: 'FAILED',
      attempts: 3,
      availableAt: '2026-08-20T07:00:00Z',
      sentAt: null,
      lastError: 'Network timeout connecting to SMS gateway',
      createdAt: '2026-08-20T07:00:00Z',
      message: 'ClinicOne: Nhac quy khach lich hen ngay mai 21/08 luc 09:00 tai Phong TQ-01.',
    },
    {
      id: 'sms-003-uuid',
      eventKey: 'APPOINTMENT_CREATED:11223344-5566-7788-99aa-bbccddeeff00',
      phone: '0901234567',
      status: 'PENDING',
      attempts: 0,
      availableAt: '2026-08-20T09:00:00Z',
      sentAt: null,
      lastError: null,
      createdAt: '2026-08-20T09:00:00Z',
      message: 'ClinicOne: Dat lich thanh cong ma LH-20260820-003. Bac si: BS. Nguyen An.',
    },
  ];

  beforeEach(async () => {
    sessionStorage.setItem('clinicOneAccessToken', 'staff-token');
    sessionStorage.setItem('clinicOneStaffRole', 'ADMIN');
    await TestBed.configureTestingModule({
      imports: [SmsDeliveryManagement],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();
    fixture = TestBed.createComponent(SmsDeliveryManagement);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    sessionStorage.clear();
  });

  it('loads and renders SMS records with friendly Vietnamese event titles', () => {
    fixture.detectChanges();
    http.expectOne('/api/v1/admin/notifications/sms').flush(mockSmsItems);
    fixture.detectChanges();

    const text = fixture.nativeElement.textContent;
    expect(text).toContain('Cảnh báo trễ lịch hẹn');
    expect(text).toContain('Nhắc lịch hẹn khám 24h');
    expect(text).toContain('Xác nhận đặt lịch khám');
    expect(text).toContain('0912345678');
    expect(text).toContain('0987654321');

    // Zero raw concatenated UUID event keys in table body
    expect(text).not.toContain('APPOINTMENT_LATE_WARNING:0b75cfa2-0c4c-4039-9541-9c30c3a2fbe9');
    expect(text).not.toContain('0b75cfa2-0c4c-4039-9541-9c30c3a2fbe9');
  });

  it('filters SMS items by phone number or Vietnamese event title', () => {
    fixture.detectChanges();
    http.expectOne('/api/v1/admin/notifications/sms').flush(mockSmsItems);
    fixture.detectChanges();

    const component = fixture.componentInstance as any;
    component.searchTerm.set('0987654321');
    fixture.detectChanges();

    expect(component.filteredItems().length).toBe(1);
    expect(component.filteredItems()[0].phone).toBe('0987654321');

    component.searchTerm.set('Cảnh báo trễ');
    fixture.detectChanges();
    expect(component.filteredItems().length).toBe(1);
    expect(component.filteredItems()[0].phone).toBe('0912345678');
  });

  it('opens preview modal with SMS message content and closes modal', () => {
    fixture.detectChanges();
    http.expectOne('/api/v1/admin/notifications/sms').flush(mockSmsItems);
    fixture.detectChanges();

    // Initial state: no modal
    expect(fixture.nativeElement.querySelector('[data-testid="sms-preview-body"]')).toBeNull();

    // Click preview button on first row
    const previewBtns = fixture.nativeElement.querySelectorAll('[data-testid="preview-sms-btn"]');
    expect(previewBtns.length).toBe(3);
    (previewBtns[0] as HTMLButtonElement).click();
    fixture.detectChanges();

    // Modal is opened
    const modalBody = fixture.nativeElement.querySelector('[data-testid="sms-preview-body"]');
    expect(modalBody).not.toBeNull();
    expect(modalBody.textContent).toContain('ClinicOne: Quy khach co lich hen LH-20260820-001');

    // Click close button
    const closeBtn = fixture.nativeElement.querySelector('[data-testid="close-preview-btn"]') as HTMLButtonElement;
    closeBtn.click();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="sms-preview-body"]')).toBeNull();
  });

  it('retries failed SMS delivery', () => {
    fixture.detectChanges();
    http.expectOne('/api/v1/admin/notifications/sms').flush(mockSmsItems);
    fixture.detectChanges();

    const component = fixture.componentInstance as any;
    component.retry(mockSmsItems[1]);

    const retryReq = http.expectOne((req) => req.url.startsWith('/api/v1/admin/notifications/sms/sms-002-uuid/retry'));
    expect(retryReq.request.method).toBe('POST');
    retryReq.flush({ ...mockSmsItems[1], status: 'PENDING' });

    // Triggers refresh
    http.expectOne('/api/v1/admin/notifications/sms').flush([
      mockSmsItems[0],
      { ...mockSmsItems[1], status: 'PENDING' },
      mockSmsItems[2],
    ]);
  });
});
