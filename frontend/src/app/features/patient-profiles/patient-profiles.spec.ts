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
});
