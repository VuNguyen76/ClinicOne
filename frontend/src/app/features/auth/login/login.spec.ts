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

  it('should keep the phone form invalid until a valid phone is entered', () => {
    expect(component.phoneForm.invalid).toBe(true);

    component.phoneForm.controls.phone.setValue('0912345678');

    expect(component.phoneForm.valid).toBe(true);
  });

  it('shows the registration action when the phone has no account', () => {
    component.phoneForm.setValue({ phone: '0912345678' });
    component['submitPhone']();

    http.expectOne('/api/v1/auth/check-phone').flush({ accountExists: false });

    expect((component as any).showRegister()).toBe(true);
    expect((component as any).step()).toBe('phone');
  });

  it('shows the password step when the phone has an account', () => {
    component.phoneForm.setValue({ phone: '0912345678' });
    component['submitPhone']();

    http.expectOne('/api/v1/auth/check-phone').flush({ accountExists: true });

    expect((component as any).step()).toBe('password');
    expect((component as any).showRegister()).toBe(false);
  });
});
