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
    http.expectOne('/api/v1/addresses/provinces?page=1&limit=100').flush([]);
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

  it('uses the outlined style for the add-profile action', () => {
    const addProfile = fixture.nativeElement.querySelector('[data-testid="add-profile-action"]') as HTMLAnchorElement | null;

    expect(addProfile?.className).toContain('border-[#0ea5e9]');
    expect(addProfile?.className).toContain('bg-white');
    expect(addProfile?.className).not.toContain('bg-[#09c3e9]');
    expect(fixture.nativeElement.querySelector('aside')?.textContent).not.toContain('Thêm hồ sơ');
  });

  it('keeps the personal profile link on the account route', () => {
    const links = Array.from(fixture.nativeElement.querySelectorAll('aside a')) as HTMLAnchorElement[];
    const profileLink = links.find((link) => link.textContent?.includes('Hồ sơ bệnh nhân'));

    expect(profileLink?.getAttribute('href')).toBe('/account');
  });
});
