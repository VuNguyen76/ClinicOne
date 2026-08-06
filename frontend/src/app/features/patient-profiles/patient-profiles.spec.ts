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
    http.expectOne('https://tinhthanhpho.com/api/v1/provinces?page=1&limit=100').flush({ success: true, message: 'Success', data: [] });
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
});
