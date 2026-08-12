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

    const requests = http.match('/api/v1/auth/request-sms-otp');
    expect(requests).toHaveLength(1);
    requests[0].flush({ expiresInSeconds: 300, retryAfterSeconds: 60 });
  });

  it('rejects mismatched passwords', () => {
    component.profileForm.setValue({ fullName: 'Nguyen Van A', dateOfBirth: '2005-06-07', gender: 'Nam', address: '', provinceCode: '', provinceName: '', districtCode: '', districtName: '', wardCode: '', wardName: '', streetAddress: '', password: 'password123', confirmPassword: 'password321' });

    expect(component.profileForm.hasError('passwordMismatch')).toBe(true);
    expect(component.profileForm.invalid).toBe(true);
  });
});
