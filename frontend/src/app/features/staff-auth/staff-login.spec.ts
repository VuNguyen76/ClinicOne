import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { Router } from '@angular/router';
import { StaffLogin } from './staff-login';

describe('StaffLogin', () => {
  let fixture: ComponentFixture<StaffLogin>;
  let http: HttpTestingController;

  beforeEach(async () => {
    sessionStorage.clear();
    await TestBed.configureTestingModule({
      imports: [StaffLogin],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([{ path: '**', component: StaffLogin }]),
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(StaffLogin);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  afterEach(() => {
    sessionStorage.clear();
    http.verify();
  });

  it('logs in a staff account and stores its role', () => {
    fixture.nativeElement.querySelector('[formcontrolname="username"]').value = 'admin';
    fixture.nativeElement.querySelector('[formcontrolname="password"]').value = 'password123';
    fixture.nativeElement.querySelector('[formcontrolname="username"]').dispatchEvent(new Event('input'));
    fixture.nativeElement.querySelector('[formcontrolname="password"]').dispatchEvent(new Event('input'));
    fixture.detectChanges();
    (fixture.nativeElement.querySelector('[data-testid="staff-login-submit"]') as HTMLButtonElement).click();

    const request = http.expectOne('/api/v1/staff/auth/login');
    expect(request.request.body).toEqual({ username: 'admin', password: 'password123' });
    request.flush({ accessToken: 'staff-token', tokenType: 'Bearer', expiresAt: '2099-01-01T00:00:00Z', staffId: 'staff-1', fullName: 'Quản trị viên', role: 'ADMIN' });

    expect(sessionStorage.getItem('clinicOneAccessToken')).toBe('staff-token');
    expect(sessionStorage.getItem('clinicOneStaffRole')).toBe('ADMIN');
  });

  it('shows the backend error without exposing credentials', () => {
    fixture.nativeElement.querySelector('[formcontrolname="username"]').value = 'admin';
    fixture.nativeElement.querySelector('[formcontrolname="password"]').value = 'wrong-password';
    fixture.nativeElement.querySelector('[formcontrolname="username"]').dispatchEvent(new Event('input'));
    fixture.nativeElement.querySelector('[formcontrolname="password"]').dispatchEvent(new Event('input'));
    fixture.detectChanges();
    (fixture.nativeElement.querySelector('[data-testid="staff-login-submit"]') as HTMLButtonElement).click();
    http.expectOne('/api/v1/staff/auth/login').flush({ error: { detail: 'Đăng nhập không thành công.' } }, { status: 401, statusText: 'Unauthorized' });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[role="alert"]').textContent).toContain('Đăng nhập không thành công');
  });

  it('sends reception staff to the reception workspace after login', () => {
    const router = TestBed.inject(Router);
    const navigateSpy = vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);
    fixture.nativeElement.querySelector('[formcontrolname="username"]').value = 'receptionist';
    fixture.nativeElement.querySelector('[formcontrolname="password"]').value = 'admin123';
    fixture.nativeElement.querySelector('[formcontrolname="username"]').dispatchEvent(new Event('input'));
    fixture.nativeElement.querySelector('[formcontrolname="password"]').dispatchEvent(new Event('input'));
    fixture.detectChanges();
    (fixture.nativeElement.querySelector('[data-testid="staff-login-submit"]') as HTMLButtonElement).click();

    http.expectOne('/api/v1/staff/auth/login').flush({
      accessToken: 'reception-token', tokenType: 'Bearer', expiresAt: '2099-01-01T00:00:00Z',
      staffId: 'staff-reception', fullName: 'Nhân viên tiếp nhận', role: 'RECEPTIONIST',
    });

    expect(navigateSpy).toHaveBeenCalledWith('/reception/check-in');
  });
});
