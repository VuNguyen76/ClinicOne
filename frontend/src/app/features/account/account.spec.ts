import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { Account } from './account';

describe('Account', () => {
  let component: Account;
  let fixture: ComponentFixture<Account>;
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Account],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(Account);
    component = fixture.componentInstance;
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    http.expectOne('https://tinhthanhpho.com/api/v1/provinces?page=1&limit=100').flush({ success: true, message: 'Success', data: [] });
    http.expectOne('/api/v1/auth/me').flush({
      accountId: 'account-1', phone: '0912345678', fullName: 'Nguyen Van A', dateOfBirth: '2005-06-07', gender: 'Nam', address: 'Tây Ninh', identityNumber: '012345678901', nationality: 'Việt Nam', ethnicity: 'Kinh', provinceCode: null, provinceName: null, districtCode: null, districtName: null, wardCode: null, wardName: null, streetAddress: null, status: 'ACTIVE', mustChangePassword: false,
    });
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  it('loads the current patient profile', () => {
    expect(fixture.nativeElement.querySelector('[formControlName="fullName"]')?.value).toBe('Nguyen Van A');
    expect(fixture.nativeElement.querySelector('[formControlName="dateOfBirth"]')?.value).toBe('2005-06-07');
    expect(fixture.nativeElement.querySelector('[formControlName="gender"]')?.value).toBe('Nam');
    expect(fixture.nativeElement.textContent).toContain('091****678');
  });

  it('links directly to the password change section', () => {
    const links = fixture.nativeElement.querySelectorAll('a') as NodeListOf<HTMLAnchorElement>;
    const link = Array.from(links).find((item) => item.textContent?.includes('Đổi mật khẩu'));

    expect(link?.getAttribute('href')).toBe('/change-password');
  });
});
