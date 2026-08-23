import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { AppointmentDetail } from './appointment-detail';

describe('AppointmentDetail', () => {
  let fixture: ComponentFixture<AppointmentDetail>;
  let component: AppointmentDetail;
  let http: HttpTestingController;

  const appointment = {
    id: 'appointment-1', appointmentCode: 'CL-001', specialty: 'Nội tổng quát', doctorName: 'BS. An',
    appointmentDate: '2099-08-10', startTime: '08:30:00', reason: 'Đau đầu',
    status: 'BOOKED', statusLabel: 'Đã đặt', doctorId: 'doctor-1', serviceId: 'service-1',
  };

  beforeEach(async () => {
    sessionStorage.setItem('clinicOneAccessToken', 'patient-token');
    await TestBed.configureTestingModule({
      imports: [AppointmentDetail],
      providers: [
        provideHttpClient(), provideHttpClientTesting(), provideRouter([]),
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: { get: () => 'appointment-1' } } } },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(AppointmentDetail);
    component = fixture.componentInstance;
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  afterEach(() => {
    http.verify();
    sessionStorage.clear();
  });

  function flushAppointment(caseResponse: object | null, alternatives: object[] = []): void {
    http.expectOne('/api/v1/appointments/appointment-1').flush(appointment);
    http.expectOne((request) => request.url === '/api/v1/reasons'
      && request.params.get('type') === 'APPOINTMENT_CANCELLATION').flush([]);
    if (caseResponse) {
      http.expectOne('/api/v1/patient/rescheduling/appointment-1').flush(caseResponse);
      http.expectOne('/api/v1/patient/rescheduling/appointment-1/alternatives').flush(alternatives);
    } else {
      http.expectOne('/api/v1/patient/rescheduling/appointment-1').flush(
        { message: 'Không tìm thấy lịch cần sắp xếp lại.' },
        { status: 404, statusText: 'Not Found' });
    }
    fixture.detectChanges();
  }

  it('loads the patient-owned rescheduling case and replacement slots', () => {
    flushAppointment({
      id: 'case-1', appointmentId: 'appointment-1', appointmentCode: 'CL-001', specialty: 'Nội tổng quát',
      oldDoctorName: 'BS. An', oldDoctorId: 'doctor-1', oldAppointmentDate: '2026-08-10', oldStartTime: '08:30:00',
      reason: 'Bác sĩ nghỉ', status: 'OPEN', newDoctorName: null, newDoctorId: null,
      newAppointmentDate: null, newStartTime: null, createdAt: '2026-08-01T00:00:00Z', resolvedAt: null,
    }, [{
      specialty: 'Nội tổng quát', appointmentDate: '2026-08-11', startTime: '09:30:00', endTime: '10:00:00',
      doctorName: 'BS. Bình', remainingCapacity: 1, doctorId: 'doctor-2', roomCode: 'NOI-01',
    }]);

    expect(fixture.nativeElement.textContent).toContain('Lịch cần đổi');
    expect(fixture.nativeElement.textContent).toContain('Bác sĩ nghỉ');
    expect(fixture.nativeElement.textContent).toContain('BS. Bình');
    expect((component as any).replacementSlots().length).toBe(1);
  });

  it('confirms the selected replacement through the patient endpoint', () => {
    flushAppointment({
      id: 'case-1', appointmentId: 'appointment-1', appointmentCode: 'CL-001', specialty: 'Nội tổng quát',
      oldDoctorName: 'BS. An', oldDoctorId: 'doctor-1', oldAppointmentDate: '2026-08-10', oldStartTime: '08:30:00',
      reason: 'Bác sĩ nghỉ', status: 'OPEN', newDoctorName: null, newDoctorId: null,
      newAppointmentDate: null, newStartTime: null, createdAt: '2026-08-01T00:00:00Z', resolvedAt: null,
    }, [{
      specialty: 'Nội tổng quát', appointmentDate: '2026-08-11', startTime: '09:30:00', endTime: '10:00:00',
      doctorName: 'BS. Bình', remainingCapacity: 1, doctorId: 'doctor-2', roomCode: 'NOI-01',
    }]);

    (component as any).chooseReplacement((component as any).replacementSlots()[0]);
    (component as any).confirmPatientReschedule();

    const request = http.expectOne('/api/v1/patient/rescheduling/appointment-1/confirm');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({
      appointmentDate: '2026-08-11', startTime: '09:30', doctorName: 'BS. Bình', doctorId: 'doctor-2',
    });
    request.flush({
      id: 'case-1', appointmentId: 'appointment-1', appointmentCode: 'CL-001', specialty: 'Nội tổng quát',
      oldDoctorName: 'BS. An', oldDoctorId: 'doctor-1', oldAppointmentDate: '2026-08-10', oldStartTime: '08:30:00',
      reason: 'Bác sĩ nghỉ', status: 'RESOLVED', newDoctorName: 'BS. Bình', newDoctorId: 'doctor-2',
      newAppointmentDate: '2026-08-11', newStartTime: '09:30:00', createdAt: '2026-08-01T00:00:00Z',
      resolvedAt: '2026-08-02T00:00:00Z',
    });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Đã xác nhận khung giờ thay thế');
  });

  it('distinguishes a replacement search failure from no available slots', () => {
    http.expectOne('/api/v1/appointments/appointment-1').flush(appointment);
    http.expectOne((request) => request.url === '/api/v1/reasons'
      && request.params.get('type') === 'APPOINTMENT_CANCELLATION').flush([]);
    http.expectOne('/api/v1/patient/rescheduling/appointment-1').flush({
      id: 'case-1', appointmentId: 'appointment-1', appointmentCode: 'CL-001', specialty: 'Nội tổng quát',
      oldDoctorName: 'BS. An', oldDoctorId: 'doctor-1', oldAppointmentDate: '2026-08-10', oldStartTime: '08:30:00',
      reason: 'Bác sĩ nghỉ', status: 'OPEN', newDoctorName: null, newDoctorId: null,
      newAppointmentDate: null, newStartTime: null, createdAt: '2026-08-01T00:00:00Z', resolvedAt: null,
    });
    http.expectOne('/api/v1/patient/rescheduling/appointment-1/alternatives').flush(
      { message: 'Dịch vụ tìm khung giờ tạm thời không khả dụng.' },
      { status: 503, statusText: 'Service Unavailable' });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Không thể tìm khung giờ thay thế lúc này');
    expect(fixture.nativeElement.textContent).not.toContain('Lịch vẫn nằm trong danh sách chờ');
  });

  it('offers QR scanning and does not allow free-form date or time rescheduling', () => {
    flushAppointment(null);

    const scanLink = fixture.nativeElement.querySelector('[data-testid="qr-scan-action"]') as HTMLAnchorElement;
    expect(scanLink.getAttribute('href')).toBe('/queue/scan');
    expect(fixture.nativeElement.querySelector('input[type="date"]')).toBeNull();
    expect(fixture.nativeElement.querySelector('input[type="time"]')).toBeNull();
    expect((component as any).rescheduleCase()).toBeNull();
  });

  it('shows only available slots of the appointment doctor when rescheduling', () => {
    flushAppointment(null);

    (fixture.nativeElement.querySelector('[data-testid="open-reschedule"]') as HTMLButtonElement).click();
    const request = http.expectOne((item) => item.url === '/api/v1/appointment-slots'
      && item.params.get('specialty') === 'Nội tổng quát'
      && item.params.get('serviceId') === 'service-1');
    request.flush([
      { specialty: 'Nội tổng quát', appointmentDate: '2026-08-19', startTime: '09:00:00', endTime: '09:30:00', doctorName: 'BS. An', doctorId: 'doctor-1', roomCode: 'NOI-01', remainingCapacity: 1 },
      { specialty: 'Nội tổng quát', appointmentDate: '2026-08-19', startTime: '10:00:00', endTime: '10:30:00', doctorName: 'BS. Bình', doctorId: 'doctor-2', roomCode: 'NOI-02', remainingCapacity: 1 },
    ]);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('09:00');
    expect(fixture.nativeElement.textContent).not.toContain('10:00');
    expect(fixture.nativeElement.textContent).toContain('BS. An');
  });

  it('locks cancel and reschedule actions and displays warning when appointment is late', () => {
    http.expectOne('/api/v1/appointments/appointment-1').flush({
      ...appointment,
      appointmentDate: '2020-01-01',
      startTime: '08:00:00',
    });
    http.expectOne((request) => request.url === '/api/v1/reasons'
      && request.params.get('type') === 'APPOINTMENT_CANCELLATION').flush([]);
    http.expectOne('/api/v1/patient/rescheduling/appointment-1').flush(
      { message: 'Không tìm thấy lịch cần sắp xếp lại.' },
      { status: 404, statusText: 'Not Found' });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Lịch hẹn đã quá giờ (Đi muộn)');
    expect(fixture.nativeElement.querySelector('[data-testid="open-reschedule"]')).toBeNull();
    expect(fixture.nativeElement.querySelector('[data-testid="qr-scan-action"]')).toBeNull();
  });

  it('renders correct color classes for cancelled (red), absent (yellow), completed (green), and booked (blue) statuses', () => {
    flushAppointment(null);
    expect(component['statusBadgeClass']('CANCELLED')).toContain('text-rose-700');
    expect(component['statusDotClass']('CANCELLED')).toBe('bg-rose-500');

    expect(component['statusBadgeClass']('ABSENT')).toContain('text-amber-700');
    expect(component['statusDotClass']('ABSENT')).toBe('bg-amber-500');

    expect(component['statusBadgeClass']('COMPLETED')).toContain('text-emerald-700');
    expect(component['statusDotClass']('COMPLETED')).toBe('bg-emerald-500');

    expect(component['statusBadgeClass']('BOOKED')).toContain('text-[#0284c7]');
    expect(component['statusDotClass']('BOOKED')).toBe('bg-[#0284c7]');
  });
});
