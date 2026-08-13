import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { Router } from '@angular/router';
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

  it('links login support to the clinic hotline instead of a placeholder action', () => {
    const supportLink = fixture.nativeElement.querySelector('a[href="tel:1900000"]') as HTMLAnchorElement | null;
    expect(supportLink).not.toBeNull();
    expect(supportLink?.textContent).toContain('Cần hỗ trợ');
  });

  it('should keep the phone form invalid until a valid phone is entered', () => {
    expect(component.phoneForm.invalid).toBe(true);

    component.phoneForm.controls.phone.setValue('0912345678');

    expect(component.phoneForm.valid).toBe(true);
  });

  it('redirects to registration when the phone has no account', () => {
    component.phoneForm.setValue({ phone: '0912345678' });
    const router = TestBed.inject(Router);
    const navigate = vi.spyOn(router, 'navigate').mockResolvedValue(true);
    component['submitPhone']();

    http.expectOne('/api/v1/auth/check-phone').flush({ accountExists: false });

    expect(navigate).toHaveBeenCalledWith(['/register'], { queryParams: { phone: '0912345678' } });
    expect((component as any).step()).toBe('phone');
  });

  it('shows the password step when the phone has an account', () => {
    component.phoneForm.setValue({ phone: '0912345678' });
    component['submitPhone']();

    http.expectOne('/api/v1/auth/check-phone').flush({ accountExists: true });

    expect((component as any).step()).toBe('password');
  });

  it('shows the server detail instead of a generic error', () => {
    component.phoneForm.setValue({ phone: '0912345678' });
    component['submitPhone']();

    http.expectOne('/api/v1/auth/check-phone').flush({ detail: 'Dịch vụ SMS đang tạm thời không khả dụng.' }, { status: 503, statusText: 'Service Unavailable' });

    expect((component as any).error()).toBe('Dịch vụ SMS đang tạm thời không khả dụng.');
  });
});
