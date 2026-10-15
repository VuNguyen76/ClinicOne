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
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  it('keeps the appointment heading and actions aligned in one header row', () => {
    http.expectOne('/api/v1/appointments').flush([]);
    http.expectOne((request) => request.url === '/api/v1/patient/queue').flush([]);
    fixture.detectChanges();
    const header = fixture.nativeElement.querySelector('[data-testid="appointments-header"]') as HTMLElement;
    expect(header).not.toBeNull();
    expect(header.querySelector('[data-testid="appointments-actions"]')).not.toBeNull();
    expect(header.querySelector('[routerlink="/appointments"]')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('[data-testid="queue-qr-scan-link"]')?.getAttribute('routerlink')).toBe('/queue/scan');
  });

  it('shows the patient queue number after QR check-in', () => {
    http.expectOne('/api/v1/appointments').flush([]);
    http.expectOne((request) => request.url === '/api/v1/patient/queue').flush([{
      id: 'ticket-1', queueNumber: 5, roomCode: 'NOI-01', roomName: 'Phòng Nội tổng quát 01', queueDate: '2026-08-09',
      appointmentTime: '09:00:00', status: 'WAITING', statusLabel: 'Đang chờ', appointmentCode: 'CL-001',
      specialty: 'Nội tổng quát', doctorName: 'BS. Nguyễn An',
    }]);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="my-queue-card"]')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('[data-testid="my-queue-card"]').textContent).toContain('05');
  });

  it('keeps completed and cancelled history out of the upcoming appointments card', () => {
    http.expectOne('/api/v1/appointments').flush([
      {
        id: 'past-completed', appointmentCode: 'PAST-REC-001', specialty: 'Khám Tổng Quát',
        doctorName: 'BS. Nguyễn An', appointmentDate: '2026-08-30', startTime: '08:00:00',
        reason: 'Khám định kỳ', status: 'COMPLETED', statusLabel: 'Đã thực hiện',
      },
      {
        id: 'upcoming-booked', appointmentCode: 'CL-FUTURE-001', specialty: 'Khám Tim Mạch',
        doctorName: 'BS. Trần Minh', appointmentDate: '2999-01-01', startTime: '09:00:00',
        reason: 'Tái khám', status: 'BOOKED', statusLabel: 'Đã đặt',
      },
      {
        id: 'upcoming-cancelled', appointmentCode: 'CL-CANCELLED-001', specialty: 'Khám Mắt',
        doctorName: 'BS. Đặng Lan', appointmentDate: '2999-01-02', startTime: '10:00:00',
        reason: 'Đã hủy', status: 'CANCELLED', statusLabel: 'Đã hủy',
      },
    ]);
    http.expectOne((request) => request.url === '/api/v1/patient/queue').flush([]);
    fixture.detectChanges();

    const dashboardText = fixture.nativeElement.textContent as string;
    expect(dashboardText).toContain('CL-FUTURE-001');
    expect(dashboardText).not.toContain('PAST-REC-001');
    expect(dashboardText).not.toContain('CL-CANCELLED-001');
  });
});
