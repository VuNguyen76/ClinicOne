import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Register } from './register';

describe('Register', () => {
  let component: Register;
  let fixture: ComponentFixture<Register>;
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Register],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(Register);
    component = fixture.componentInstance;
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    http.expectOne('/api/v1/addresses/provinces?page=1&limit=100').flush([]);
  });

  afterEach(() => http.verify());

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('requires a valid phone before requesting registration OTP', () => {
    expect(component.phoneForm.invalid).toBe(true);

    component.phoneForm.controls.phone.setValue('0912345678');

    expect(component.phoneForm.valid).toBe(true);
  });

  it('moves through SMS verification and account details', () => {
    component.phoneForm.controls.phone.setValue('0912345678');
    component['submitPhone']();
    http.expectOne('/api/v1/auth/check-phone').flush({ accountExists: false });
    http.expectOne('/api/v1/auth/request-sms-otp').flush({ expiresInSeconds: 300, retryAfterSeconds: 60 });
    expect((component as any).step()).toBe('otp');

    component.otpForm.controls.code.setValue('123456');
    component['submitOtp']();
    http.expectOne('/api/v1/auth/verify-sms-otp').flush({ verified: true });
    expect((component as any).step()).toBe('profile');

    component.profileForm.setValue({ fullName: 'Nguyen Van A', dateOfBirth: '2005-06-07', gender: 'Nam', address: 'Tay Ninh', provinceCode: '', provinceName: '', districtCode: '', districtName: '', wardCode: '', wardName: '', streetAddress: '', password: 'password123', confirmPassword: 'password123' });
    component['submitProfile']();
    http.expectOne('/api/v1/auth/register').flush({ accountId: 'account-1', phone: '0912345678', fullName: 'Nguyen Van A' });
    expect((component as any).step()).toBe('done');
  });

  it('does not request two OTP messages from repeated submit events', () => {
    component.phoneForm.controls.phone.setValue('0912345678');

    component['submitPhone']();
    component['submitPhone']();

    const checkRequests = http.match('/api/v1/auth/check-phone');
    expect(checkRequests).toHaveLength(1);
    checkRequests[0].flush({ accountExists: false });

    const requests = http.match('/api/v1/auth/request-sms-otp');
    expect(requests).toHaveLength(1);
    requests[0].flush({ expiresInSeconds: 300, retryAfterSeconds: 60 });
  });

  it('does not navigate away during OTP verification and then jump forward on the late response', () => {
    component['phone'].set('0912345678');
    component['step'].set('otp');
    component.otpForm.controls.code.setValue('123456');
    component['submitOtp']();
    const request = http.expectOne('/api/v1/auth/verify-sms-otp');

    component['backToPhone']();
    expect(component['step']()).toBe('otp');
    request.flush({ verified: true });

    expect(component['step']()).toBe('profile');
  });

  it('does not apply districts from a province that is no longer selected', () => {
    component['provinces'].set([{ code: '01', name: 'Province A' }, { code: '02', name: 'Province B' }]);
    component.profileForm.controls.provinceCode.setValue('01');
    component['provinceChanged']();
    const firstRequest = http.expectOne('/api/v1/addresses/provinces/01/districts?page=1&limit=100');

    component.profileForm.controls.provinceCode.setValue('02');
    component['provinceChanged']();
    const currentRequest = http.expectOne('/api/v1/addresses/provinces/02/districts?page=1&limit=100');
    const currentDistrict = { code: '20', name: 'District B' };
    firstRequest.flush([{ code: '10', name: 'District A' }]);
    expect(component['addressLoading']()).toBe(true);
    currentRequest.flush([currentDistrict]);

    expect(component['districts']()).toEqual([currentDistrict]);
  });

  it('does not apply wards from a district that is no longer selected', () => {
    component['districts'].set([{ code: '10', name: 'District A' }, { code: '20', name: 'District B' }]);
    component.profileForm.controls.districtCode.setValue('10');
    component['districtChanged']();
    const firstRequest = http.expectOne('/api/v1/addresses/districts/10/wards?page=1&limit=100');

    component.profileForm.controls.districtCode.setValue('20');
    component['districtChanged']();
    const currentRequest = http.expectOne('/api/v1/addresses/districts/20/wards?page=1&limit=100');
    const currentWard = { code: '200', name: 'Ward B' };
    currentRequest.flush([currentWard]);
    firstRequest.flush([{ code: '100', name: 'Ward A' }]);

    expect(component['wards']()).toEqual([currentWard]);
  });

  it('sends only one OTP verification for repeated submit events', () => {
    component['phone'].set('0912345678');
    component['step'].set('otp');
    component.otpForm.controls.code.setValue('123456');

    component['submitOtp']();
    component['submitOtp']();

    const requests = http.match('/api/v1/auth/verify-sms-otp');
    expect(requests).toHaveLength(1);
    requests[0].flush({ verified: true });
  });

  it('blocks registration and shows prompt if phone already registered', () => {
    component.phoneForm.controls.phone.setValue('0912345678');
    component['submitPhone']();

    http.expectOne('/api/v1/auth/check-phone').flush({ accountExists: true });

    expect((component as any).step()).toBe('phone');
    expect((component as any).existingAccount()).toBe(true);
    expect((component as any).error()).toContain('đã được đăng ký tài khoản');
    http.expectNone('/api/v1/auth/request-sms-otp');
  });

  it('handles phone already used error returned from request-sms-otp directly', () => {
    component.phoneForm.controls.phone.setValue('0912345678');
    component['submitPhone']();

    http.expectOne('/api/v1/auth/check-phone').error(new ProgressEvent('error'));
    http.expectOne('/api/v1/auth/request-sms-otp').flush(
      { message: 'Số điện thoại này đã được đăng ký tài khoản.' },
      { status: 409, statusText: 'Conflict' }
    );

    expect((component as any).step()).toBe('phone');
    expect((component as any).existingAccount()).toBe(true);
  });

  it('clears existingAccount error when user changes phone number', () => {
    component.phoneForm.controls.phone.setValue('0912345678');
    component['submitPhone']();
    http.expectOne('/api/v1/auth/check-phone').flush({ accountExists: true });

    expect((component as any).existingAccount()).toBe(true);
    expect((component as any).error()).not.toBe('');

    component.phoneForm.controls.phone.setValue('0987654321');
    expect((component as any).existingAccount()).toBe(false);
    expect((component as any).error()).toBe('');
  });

  it('rejects mismatched passwords', () => {
    component.profileForm.setValue({ fullName: 'Nguyen Van A', dateOfBirth: '2005-06-07', gender: 'Nam', address: '', provinceCode: '', provinceName: '', districtCode: '', districtName: '', wardCode: '', wardName: '', streetAddress: '', password: 'password123', confirmPassword: 'password321' });

    expect(component.profileForm.hasError('passwordMismatch')).toBe(true);
    expect(component.profileForm.invalid).toBe(true);
  });
});
