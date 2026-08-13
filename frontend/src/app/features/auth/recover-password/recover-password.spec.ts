import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RecoverPassword } from './recover-password';

describe('RecoverPassword', () => {
  let component: RecoverPassword;
  let fixture: ComponentFixture<RecoverPassword>;
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [RecoverPassword],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(RecoverPassword);
    component = fixture.componentInstance;
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  it('requires a valid phone before requesting recovery OTP', () => {
    expect(component.phoneForm.invalid).toBe(true);
    component.phoneForm.controls.phone.setValue('0912345678');
    expect(component.phoneForm.valid).toBe(true);
  });

  it('verifies recovery OTP and changes the password through BE contracts', () => {
    component.phoneForm.controls.phone.setValue('0912345678');
    component['requestOtp']();
    http.expectOne('/api/v1/auth/request-sms-otp').flush({ expiresInSeconds: 300, retryAfterSeconds: 60 });
    expect((component as any).step()).toBe('otp');

    component.otpForm.controls.code.setValue('123456');
    component['verifyOtp']();
    http.expectOne('/api/v1/auth/verify-sms-otp').flush({ verified: true });
    expect((component as any).step()).toBe('password');

    component.passwordForm.setValue({ newPassword: 'newPassword123', confirmPassword: 'newPassword123' });
    component['resetPassword']();
    const request = http.expectOne('/api/v1/auth/recover-password');
    expect(request.request.body).toEqual({ phone: '0912345678', newPassword: 'newPassword123', confirmPassword: 'newPassword123' });
    request.flush(null);
    expect((component as any).step()).toBe('done');
  });

  it('does not send duplicate OTP requests while the first request is pending', () => {
    component.phoneForm.controls.phone.setValue('0912345678');
    component['requestOtp']();
    component['requestOtp']();
    const requests = http.match('/api/v1/auth/request-sms-otp');
    expect(requests).toHaveLength(1);
    requests[0].flush({ expiresInSeconds: 300, retryAfterSeconds: 60 });
  });
});
