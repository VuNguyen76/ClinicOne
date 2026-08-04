import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { Login } from './login';

describe('Login', () => {
  let component: Login;
  let fixture: ComponentFixture<Login>;
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Login],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(Login);
    component = fixture.componentInstance;
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should keep the form invalid until valid credentials are entered', () => {
    expect(component.phoneForm.invalid).toBe(true);

    component.phoneForm.controls.phone.setValue('0912345678');
    component.phoneForm.controls.password.setValue('password123');

    expect(component.phoneForm.valid).toBe(true);
  });

  it('moves to OTP immediately without waiting for SMS request response', () => {
    component.phoneForm.setValue({ phone: '0912345678', password: 'password123' });
    component['submitCredentials']();

    expect((component as any).step()).toBe('otp');
    expect((component as any).sendingOtp()).toBe(true);

    http.expectOne('/api/v1/auth/request-sms-otp').flush({ expiresInSeconds: 300, retryAfterSeconds: 60 });
    expect((component as any).sendingOtp()).toBe(false);
  });
});
