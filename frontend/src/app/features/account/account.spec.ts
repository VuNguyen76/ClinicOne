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

  it('keeps districts for the latest selected province when responses arrive out of order', () => {
    component['provinces'].set([
      { code: '01', name: 'Province A' },
      { code: '02', name: 'Province B' },
    ]);
    component['profileForm'].controls.provinceCode.setValue('01');
    component['provinceChanged']();
    const provinceA = http.expectOne('/api/v1/addresses/provinces/01/districts?page=1&limit=100');

    component['profileForm'].controls.provinceCode.setValue('02');
    component['provinceChanged']();
    const provinceB = http.expectOne('/api/v1/addresses/provinces/02/districts?page=1&limit=100');

    provinceB.flush([{ code: '0201', name: 'District B' }]);
    provinceA.flush([{ code: '0101', name: 'District A' }]);

    expect(component['districts']().map((item) => item.name)).toEqual(['District B']);
  });

  it('does not apply profile-prefill address responses after the province changes', () => {
    component['setProfileForm']({
      accountId: 'account-1', phone: '0912345678', fullName: 'Nguyen Van A', dateOfBirth: '2005-06-07', gender: 'Nam',
      address: '', identityNumber: '', nationality: 'Việt Nam', ethnicity: 'Kinh', provinceCode: '01', provinceName: 'Province A',
      districtCode: '0101', districtName: 'District A', wardCode: '010101', wardName: 'Ward A', streetAddress: '',
      status: 'ACTIVE', mustChangePassword: false,
    });
    const oldDistricts = http.expectOne('/api/v1/addresses/provinces/01/districts?page=1&limit=100');
    const oldWards = http.expectOne('/api/v1/addresses/districts/0101/wards?page=1&limit=100');

    component['provinces'].set([{ code: '02', name: 'Province B' }]);
    component['profileForm'].controls.provinceCode.setValue('02');
    component['provinceChanged']();
    const currentDistricts = http.expectOne('/api/v1/addresses/provinces/02/districts?page=1&limit=100');
    currentDistricts.flush([{ code: '0201', name: 'District B' }]);
    oldDistricts.flush([{ code: '0101', name: 'District A' }]);
    oldWards.flush([{ code: '010101', name: 'Ward A' }]);

    expect(component['districts']().map((item) => item.name)).toEqual(['District B']);
    expect(component['wards']()).toEqual([]);
  });

  it('sends the selected address parts as one display address', () => {
    component['profileForm'].patchValue({
      provinceCode: '72', provinceName: 'Tây Ninh',
      districtCode: '718', districtName: 'Trảng Bàng',
      wardCode: '25771', wardName: 'An Tịnh',
      streetAddress: '3828', address: '',
    });

    component['saveProfile']();

    const request = http.expectOne((item) => item.url === '/api/v1/auth/me' && item.method === 'PATCH');
    expect(request.request.body.address).toBe('3828, An Tịnh, Trảng Bàng, Tây Ninh');
    request.flush({
      accountId: 'account-1', phone: '0912345678', fullName: 'Nguyen Van A', dateOfBirth: '2005-06-07',
      gender: 'Nam', address: '3828, An Tịnh, Trảng Bàng, Tây Ninh', identityNumber: '012345678901',
      nationality: 'Việt Nam', ethnicity: 'Kinh', provinceCode: '72', provinceName: 'Tây Ninh',
      districtCode: '718', districtName: 'Trảng Bàng', wardCode: '25771', wardName: 'An Tịnh',
      streetAddress: '3828', status: 'ACTIVE', mustChangePassword: false,
    });
    http.expectOne('/api/v1/addresses/provinces/72/districts?page=1&limit=100').flush([]);
    http.expectOne('/api/v1/addresses/districts/718/wards?page=1&limit=100').flush([]);
  });

  it('prevents profile edits from being overwritten while a save is pending', () => {
    component['saveProfile']();
    fixture.detectChanges();
    const fullNameInput = fixture.nativeElement.querySelector('[formControlName="fullName"]') as HTMLInputElement;
    expect(fullNameInput.disabled).toBe(true);

    http.expectOne((item) => item.url === '/api/v1/auth/me' && item.method === 'PATCH').flush({
      accountId: 'account-1', phone: '0912345678', fullName: 'Nguyen Van A', dateOfBirth: '2005-06-07',
      gender: 'Nam', address: 'Tây Ninh', identityNumber: '012345678901', nationality: 'Việt Nam', ethnicity: 'Kinh',
      provinceCode: null, provinceName: null, districtCode: null, districtName: null, wardCode: null, wardName: null,
      streetAddress: null, status: 'ACTIVE', mustChangePassword: false,
    });
    fixture.detectChanges();

    expect(fullNameInput.disabled).toBe(false);
  });
});
