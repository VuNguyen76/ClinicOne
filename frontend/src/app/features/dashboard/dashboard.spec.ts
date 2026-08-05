import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { Dashboard } from './dashboard';

describe('Dashboard', () => {
  let fixture: ComponentFixture<Dashboard>;
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Dashboard],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(Dashboard);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    http.expectOne('/api/v1/auth/me').flush({ accountId: 'a-1', phone: '0900000000', fullName: 'Nguyễn An', dateOfBirth: '2000-01-01', gender: 'Nam', address: null, identityNumber: null, nationality: null, ethnicity: null, provinceCode: null, provinceName: null, districtCode: null, districtName: null, wardCode: null, wardName: null, streetAddress: null, status: 'ACTIVE', mustChangePassword: false });
    http.expectOne('/api/v1/appointments').flush([]);
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  it('keeps the appointment heading and actions aligned in one header row', () => {
    const header = fixture.nativeElement.querySelector('[data-testid="appointments-header"]') as HTMLElement;
    expect(header).not.toBeNull();
    expect(header.querySelector('[data-testid="appointments-actions"]')).not.toBeNull();
    expect(header.querySelector('[routerlink="/appointments"]')).not.toBeNull();
  });
});
