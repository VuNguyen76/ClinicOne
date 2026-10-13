import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { DoctorSchedule } from './doctor-schedule';

describe('DoctorSchedule', () => {
  let fixture: ComponentFixture<DoctorSchedule>;
  let http: HttpTestingController;

  beforeEach(async () => {
    sessionStorage.setItem('clinicOneStaffRole', 'DOCTOR');
    await TestBed.configureTestingModule({
      imports: [DoctorSchedule],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();
    fixture = TestBed.createComponent(DoctorSchedule);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  afterEach(() => {
    fixture?.destroy();
    http.verify();
    sessionStorage.clear();
  });

  it('loads the signed-in doctor schedule and renders weekly hours and breaks', () => {
    const request = http.expectOne('/api/v1/doctor/schedule-templates');
    expect(request.request.method).toBe('GET');
    request.flush([scheduleTemplate()]);
    fixture.detectChanges();

    const page = fixture.nativeElement as HTMLElement;
    expect(page.querySelector('[data-testid="doctor-timetable"]')).not.toBeNull();
    expect(page.querySelector('[data-testid="doctor-timetable"] header')).toBeNull();
    expect(page.textContent).not.toMatch(/\d+ lịch/);
    expect(page.querySelectorAll('[data-testid="doctor-day-header"]')).toHaveLength(7);
    expect(page.querySelectorAll('[data-testid="doctor-today-marker"]')).toHaveLength(1);
    expect(page.textContent).toContain('Thứ 3');
    expect(page.textContent).toContain('08:00 – 17:00');
    expect(page.textContent).toContain('Nghỉ 12:00 – 13:00');
    expect(page.textContent).toContain('NOI-01');
    expect(page.querySelector('[data-testid="doctor-profile-avatar"]')?.getAttribute('src')).toBe('https://clinic.test/doctor.png');
    const shiftCard = page.querySelector('[data-testid="doctor-shift-card"]');
    expect(shiftCard).not.toBeNull();
    expect(shiftCard?.querySelector('img')?.getAttribute('src')).toBe('https://clinic.test/doctor.png');
    expect(shiftCard?.textContent).toContain('NOI-01');
    expect(page.textContent).toContain('BS. Nguyễn An');
  });

  it('shows an explicit empty state when the doctor has no active schedule', () => {
    http.expectOne('/api/v1/doctor/schedule-templates').flush([]);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Chưa có thời khóa biểu được phân công.');
  });
});

function scheduleTemplate() {
  return {
    id: 'template-1', clinicServiceId: 'service-1', serviceName: 'Khám tổng quát',
    specialty: 'Khám Tổng Quát', visitType: 'Khám thường', durationMinutes: 30,
    doctorId: 'doctor-1', doctorName: 'BS. Nguyễn An', roomId: 'room-1', roomCode: 'NOI-01',
    doctorAvatarUrl: 'https://clinic.test/doctor.png',
    startDate: '2026-01-01', endDate: '2026-12-31', weekdays: ['TUESDAY'],
    dayStart: '08:00:00', dayEnd: '17:00:00',
    breaks: [{ startTime: '12:00:00', endTime: '13:00:00' }],
    exceptionDates: [], generatedSlotCount: 10, active: true,
  };
}
