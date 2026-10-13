import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { DoctorAppointments } from './doctor-appointments';
import { clinicTodayIso } from '../../core/time/clinic-time';

describe('DoctorAppointments', () => {
  let fixture: ComponentFixture<DoctorAppointments>;
  let http: HttpTestingController;

  beforeEach(async () => {
    sessionStorage.setItem('clinicOneAccessToken', 'staff-token');
    sessionStorage.setItem('clinicOneSessionType', 'STAFF');
    sessionStorage.setItem('clinicOneStaffRole', 'DOCTOR');
    await TestBed.configureTestingModule({
      imports: [DoctorAppointments],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();
    fixture = TestBed.createComponent(DoctorAppointments);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  afterEach(() => {
    fixture?.destroy();
    http.verify();
    sessionStorage.clear();
  });

  it('loads and displays the signed-in doctor appointment agenda', () => {
    const request = http.expectOne((req) => req.url === '/api/v1/doctor/appointments');
    expect(request.request.method).toBe('GET');
    expect(request.request.params.get('from')).toBe(clinicTodayIso());
    expect(request.request.params.get('to')).toBeTruthy();
    request.flush([appointment()]);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Lịch hẹn của tôi');
    expect(fixture.nativeElement.querySelectorAll('[data-testid="doctor-appointment-row"]').length).toBe(1);
    expect(fixture.nativeElement.textContent).toContain('Nguyễn Thị Mai');
    expect(fixture.nativeElement.textContent).toContain('Đã đặt');
  });

  it('does not request appointments when the selected date range is reversed', () => {
    http.expectOne((req) => req.url === '/api/v1/doctor/appointments').flush([]);
    fixture.detectChanges();
    const component = fixture.componentInstance as unknown as {
      from: { set(value: string): void };
      to: { set(value: string): void };
      searchAppointments(): void;
    };
    component.from.set('2026-11-12');
    component.to.set('2026-10-13');
    component.searchAppointments();

    http.expectNone((req) => req.url === '/api/v1/doctor/appointments');
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[role="alert"]')?.textContent).toContain('Ngày bắt đầu');
  });
});

function appointment() {
  return {
    id: 'appointment-1', appointmentCode: 'CL-20261013-0001', specialty: 'Nội khoa',
    doctorName: 'Bác sĩ Nguyễn An', appointmentDate: clinicTodayIso(), startTime: '09:00:00',
    reason: 'Tái khám', status: 'BOOKED', statusLabel: 'Đã đặt',
    profileId: 'profile-1', profileName: 'Nguyễn Thị Mai', doctorId: 'doctor-1',
    serviceId: 'service-1', serviceName: 'Khám nội tổng quát', visitType: 'Khám thường',
    serviceDurationMinutes: 30, requiresMedicalRecord: true,
  };
}
