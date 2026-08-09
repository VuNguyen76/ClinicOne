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
    http.expectOne((request) => request.url === '/api/v1/patient/queue').flush([]);
    fixture.detectChanges();
    const header = fixture.nativeElement.querySelector('[data-testid="appointments-header"]') as HTMLElement;
    expect(header).not.toBeNull();
    expect(header.querySelector('[data-testid="appointments-actions"]')).not.toBeNull();
    expect(header.querySelector('[routerlink="/appointments"]')).not.toBeNull();
  });

  it('shows the patient queue number after QR check-in', () => {
    http.expectOne((request) => request.url === '/api/v1/patient/queue').flush([{
      id: 'ticket-1', queueNumber: 5, roomCode: 'NOI-01', roomName: 'Phòng Nội tổng quát 01', queueDate: '2026-08-09',
      appointmentTime: '09:00:00', status: 'WAITING', statusLabel: 'Đang chờ', appointmentCode: 'CL-001',
      specialty: 'Nội tổng quát', doctorName: 'BS. Nguyễn An',
    }]);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="my-queue-card"]')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('[data-testid="my-queue-card"]').textContent).toContain('05');
  });
});
