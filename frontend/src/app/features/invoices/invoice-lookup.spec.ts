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
    await TestBed.configureTestingModule({
      imports: [InvoiceLookup],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { queryParamMap: { get: () => null } } },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(InvoiceLookup);
    component = fixture.componentInstance;
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  it('starts with phone lookup and no invoice list or receipt-code input', () => {
    const text = fixture.nativeElement.textContent;
    expect(text).toContain('Tra cứu PTT & đơn thuốc');
    expect(text).toContain('Số điện thoại');
    expect(text).not.toContain('Mã phiếu');
    expect(text).not.toContain('Liên hệ');
    expect(http.match('/api/v1/invoices')).toHaveLength(0);
  });

  it('requests a purpose-specific OTP for the entered phone', () => {
    component.phone.set('0862764830');
    component.requestOtp();
    const request = http.expectOne('/api/v1/auth/request-sms-otp');
    expect(request.request.body).toEqual({ phone: '0862764830', purpose: 'RECEIPT_LOOKUP' });
    request.flush({ expiresInSeconds: 300, retryAfterSeconds: 60 });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Mã OTP');
  });

  it('verifies OTP then lets the user choose among profiles and view prescriptions', () => {
    component.phone.set('0862764830');
    component.requestOtp();
    http.expectOne('/api/v1/auth/request-sms-otp').flush({ expiresInSeconds: 300, retryAfterSeconds: 60 });
    component.code.set('123456');
    component.lookup();

    const request = http.expectOne('/api/v1/public/patient-data/lookup');
    expect(request.request.body).toEqual({ phone: '0862764830', code: '123456' });
    request.flush({ profiles: [
      { profileId: 'profile-1', fullName: 'Nguyễn An', relationship: 'Con', invoices: [], medicalRecords: [
        { id: 'record-1', signedAt: '2026-10-12T02:00:00Z', doctorName: 'Bác sĩ A', specialty: 'Nhi khoa',
          diagnosis: 'Cảm cúm', prescription: null, prescriptionLines: [
            { medicationName: 'Thuốc A', dosage: '1 viên', quantity: 5, unit: 'viên', instructions: 'Ngày 2 lần' },
          ] },
      ] },
      { profileId: 'profile-2', fullName: 'Nguyễn Bình', relationship: 'Bản thân', invoices: [], medicalRecords: [] },
    ] });
    fixture.detectChanges();

    expect(component.selectedProfileId()).toBeNull();
    expect(fixture.nativeElement.textContent).toContain('Nguyễn An');
    const profileButton: HTMLButtonElement = fixture.nativeElement.querySelector('[data-testid="select-profile-profile-1"]');
    profileButton.click();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('PTT');

    (fixture.nativeElement.querySelector('[data-testid="prescription-tab"]') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Thuốc A');
    expect(fixture.nativeElement.textContent).toContain('Ngày 2 lần');
  });

  it('auto-selects the only matching profile', () => {
    component.phone.set('0862764830');
    component.requestOtp();
    http.expectOne('/api/v1/auth/request-sms-otp').flush({ expiresInSeconds: 300, retryAfterSeconds: 60 });
    component.code.set('123456');
    component.lookup();
    http.expectOne('/api/v1/public/patient-data/lookup').flush({ profiles: [
      { profileId: 'profile-1', fullName: 'Nguyễn An', relationship: 'Bản thân', invoices: [], medicalRecords: [] },
    ] });

    expect(component.selectedProfileId()).toBe('profile-1');
  });

  it('rejects a malformed OTP before calling the lookup endpoint', () => {
    component.phone.set('0862764830');
    component.code.set('123');
    component.lookup();

    expect(component.error()).toContain('6 chữ số');
    expect(http.match('/api/v1/public/patient-data/lookup')).toHaveLength(0);
  });
});
