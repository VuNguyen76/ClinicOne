import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { ScheduleTemplateManagement } from './schedule-template-management';

describe('ScheduleTemplateManagement', () => {
  let fixture: ComponentFixture<ScheduleTemplateManagement>;
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ScheduleTemplateManagement],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(ScheduleTemplateManagement);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  it('loads services, assigned doctors, rooms and existing templates', () => {
    http.expectOne('/api/v1/admin/services/active').flush([service()]);
    http.expectOne('/api/v1/admin/doctors').flush([doctor()]);
    http.expectOne('/api/v1/rooms').flush([room()]);
    http.expectOne('/api/v1/admin/schedule-templates').flush([]);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Mẫu lịch làm việc');
    expect(fixture.nativeElement.querySelectorAll('[data-testid="template-row"]').length).toBe(0);
  });

  it('creates a template with the selected service, doctor, room and weekdays', () => {
    http.expectOne('/api/v1/admin/services/active').flush([service()]);
    http.expectOne('/api/v1/admin/doctors').flush([doctor()]);
    http.expectOne('/api/v1/rooms').flush([room()]);
    http.expectOne('/api/v1/admin/schedule-templates').flush([]);
    fixture.detectChanges();

    const component = fixture.componentInstance as any;
    component.selectedServiceId.set('service-1');
    component.selectedDoctorId.set('doctor-1');
    component.selectedRoomId.set('room-1');
    fixture.detectChanges();

    (fixture.nativeElement.querySelector('[data-testid="save-template"]') as HTMLButtonElement).click();
    const request = http.expectOne('/api/v1/admin/schedule-templates');
    expect(request.request.method).toBe('POST');
    expect(request.request.body.clinicServiceId).toBe('service-1');
    expect(request.request.body.doctorId).toBe('doctor-1');
    expect(request.request.body.roomId).toBe('room-1');
    expect(request.request.body.durationMinutes).toBe(30);
    expect(request.request.body.weekdays.length).toBeGreaterThan(0);
    request.flush({ ...template(), id: 'template-1', generatedSlotCount: 16 });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelectorAll('[data-testid="template-row"]').length).toBe(1);
  });
});

function service() {
  return { id: 'service-1', name: 'Khám tổng quát cơ bản', specialty: 'Khám Tổng Quát', visitType: 'Khám thường',
    durationMinutes: 30, active: true, eligibleDoctors: [{ doctorProfileId: 'profile-1', staffId: 'doctor-1', fullName: 'Bác sĩ Nguyễn An' }] };
}

function doctor() {
  return { staffId: 'doctor-1', username: 'doctor-an', fullName: 'Bác sĩ Nguyễn An', specialty: 'Khám Tổng Quát',
    roomId: 'room-1', roomCode: 'TQ-01', roomName: 'Phòng Tổng Quát 01', assigned: true, active: true };
}

function room() {
  return { id: 'room-1', code: 'TQ-01', name: 'Phòng Tổng Quát 01', specialty: 'Khám Tổng Quát', active: true, qrToken: 'qr' };
}

function template() {
  return { id: 'template-1', clinicServiceId: 'service-1', serviceName: 'Khám tổng quát cơ bản', specialty: 'Khám Tổng Quát',
    visitType: 'Khám thường', durationMinutes: 30, doctorId: 'doctor-1', doctorName: 'Bác sĩ Nguyễn An', roomId: 'room-1',
    roomCode: 'TQ-01', startDate: '2026-08-10', endDate: '2026-08-31', weekdays: ['MONDAY'], dayStart: '08:00', dayEnd: '17:00',
    breaks: [], exceptionDates: [], generatedSlotCount: 16, active: true };
}
