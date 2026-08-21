import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { ChangePassword } from './change-password';

describe('ChangePassword', () => {
  let component: ChangePassword;
  let fixture: ComponentFixture<ChangePassword>;
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ChangePassword],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(ChangePassword);
    component = fixture.componentInstance;
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  it('requires matching passwords', () => {
    component.passwordForm.setValue({ currentPassword: 'old-pass', newPassword: 'new-password', confirmPassword: 'different' });

    expect(component.passwordForm.invalid).toBe(true);
    expect(component.passwordForm.hasError('passwordMismatch')).toBe(true);
  });

  it('changes the password successfully', () => {
    component.passwordForm.setValue({ currentPassword: 'old-pass', newPassword: 'new-password', confirmPassword: 'new-password' });
    component['savePassword']();

    http.expectOne('/api/v1/auth/me/password').flush({});

    expect((component as any).notice()).toBe('Mật khẩu đã được đổi thành công.');
  });
});
