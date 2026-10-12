import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { PatientProfiles } from './patient-profiles';

describe('PatientProfiles', () => {
  let component: PatientProfiles;
  let fixture: ComponentFixture<PatientProfiles>;
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [PatientProfiles],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(PatientProfiles);
    component = fixture.componentInstance;
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    http.expectOne('/api/v1/addresses/provinces?page=1&limit=100').flush([]);
    http.expectOne('/api/v1/patient-profiles').flush([{ id: 'p-1', fullName: 'Nguyễn An', relationship: 'Bản thân', dateOfBirth: '2000-01-01', gender: 'Nam', phone: null, identityNumber: null, nationality: 'Việt Nam', ethnicity: 'Kinh', address: null, provinceCode: null, provinceName: null, districtCode: null, districtName: null, wardCode: null, wardName: null, streetAddress: null, primaryProfile: true }]);
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  it('loads profiles and shows the primary profile', () => {
    expect(component['profiles']().length).toBe(1);
    expect(fixture.nativeElement.textContent).toContain('Nguyễn An');
    expect(fixture.nativeElement.textContent).toContain('Bản thân');
  });

  it('opens an empty form for adding a profile', () => {
    component['startAdd']();
    expect(component['formOpen']()).toBe(true);
    expect(component['editingId']()).toBeNull();
  });

  it('sends the selected address parts as one display address', () => {
    component['startAdd']();
    component['form'].patchValue({
      fullName: 'Nguyễn An', dateOfBirth: '2000-01-01', gender: 'Nam', relationship: 'Người thân',
      provinceCode: '72', provinceName: 'Tây Ninh',
      districtCode: '718', districtName: 'Trảng Bàng',
      wardCode: '25771', wardName: 'An Tịnh', streetAddress: '3828', address: '',
    });

    component['save']();

    const request = http.expectOne((item) => item.url === '/api/v1/patient-profiles' && item.method === 'POST');
    expect(request.request.body.address).toBe('3828, An Tịnh, Trảng Bàng, Tây Ninh');
    request.flush({
      id: 'p-2', fullName: 'Nguyễn An', relationship: 'Người thân', dateOfBirth: '2000-01-01',
      gender: 'Nam', phone: null, identityNumber: null, nationality: 'Việt Nam', ethnicity: 'Kinh',
      address: '3828, An Tịnh, Trảng Bàng, Tây Ninh', provinceCode: '72', provinceName: 'Tây Ninh',
      districtCode: '718', districtName: 'Trảng Bàng', wardCode: '25771', wardName: 'An Tịnh',
      streetAddress: '3828', primaryProfile: false,
    });
    http.expectOne('/api/v1/patient-profiles').flush([]);
  });

  it('does not let address data from a previous profile edit replace the current profile options', () => {
    const profile = (id: string, provinceCode: string, districtCode: string) => ({
      id, fullName: id, relationship: 'Người thân', dateOfBirth: '2000-01-01', gender: 'Nam',
      primaryProfile: false, phone: null, identityNumber: null, nationality: 'Việt Nam', ethnicity: 'Kinh',
      provinceCode, provinceName: `Tỉnh ${provinceCode}`,
      districtCode, districtName: `Huyện ${districtCode}`, wardCode: '', wardName: '',
      streetAddress: '', address: '',
    });
    component['edit'](profile('profile-old', '72', '718'));
    const oldDistricts = http.expectOne('/api/v1/addresses/provinces/72/districts?page=1&limit=100');
    const oldWards = http.expectOne('/api/v1/addresses/districts/718/wards?page=1&limit=100');

    component['edit'](profile('profile-current', '79', '719'));
    const currentDistricts = http.expectOne('/api/v1/addresses/provinces/79/districts?page=1&limit=100');
    const currentWards = http.expectOne('/api/v1/addresses/districts/719/wards?page=1&limit=100');
    currentDistricts.flush([{ code: '719', name: 'Huyện hiện tại' }]);
    currentWards.flush([{ code: '25774', name: 'Phường hiện tại' }]);
    oldDistricts.flush([{ code: '718', name: 'Huyện cũ' }]);
    oldWards.flush([{ code: '25771', name: 'Phường cũ' }]);

    expect(component['editingId']()).toBe('profile-current');
    expect(component['districts']().map((item: any) => item.name)).toEqual(['Huyện hiện tại']);
    expect(component['wards']().map((item: any) => item.name)).toEqual(['Phường hiện tại']);
  });

  it('keeps district options tied to the latest province selection', () => {
    component['startAdd']();
    component['form'].controls.provinceCode.setValue('72');
    component['provinceChanged']();
    const oldRequest = http.expectOne('/api/v1/addresses/provinces/72/districts?page=1&limit=100');

    component['form'].controls.provinceCode.setValue('79');
    component['provinceChanged']();
    const currentRequest = http.expectOne('/api/v1/addresses/provinces/79/districts?page=1&limit=100');
    currentRequest.flush([{ code: '769', name: 'Quận hiện tại' }]);
    oldRequest.flush([{ code: '718', name: 'Huyện cũ' }]);

    expect(component['districts']().map((item: any) => item.code)).toEqual(['769']);
    expect(component['wards']()).toEqual([]);
    expect(component['addressLoading']()).toBe(false);
  });
});
