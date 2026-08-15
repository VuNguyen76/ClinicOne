import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { DoctorTimeOffManagement } from './doctor-time-off';

describe('DoctorTimeOffManagement', () => {
  let fixture: ComponentFixture<DoctorTimeOffManagement>;
  let http: HttpTestingController;

  beforeEach(async () => {
    sessionStorage.setItem('clinicOneAccessToken', 'staff-token');
    sessionStorage.setItem('clinicOneStaffRole', 'COORDINATOR');
    await TestBed.configureTestingModule({
      imports: [DoctorTimeOffManagement],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();
    fixture = TestBed.createComponent(DoctorTimeOffManagement);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  afterEach(() => { http.verify(); sessionStorage.clear(); });

  it('loads doctors and existing time off records', () => {
    http.expectOne('/api/v1/admin/doctors').flush([doctor()]);
    http.expectOne('/api/v1/admin/doctor-time-off').flush([]);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Bác sĩ nghỉ đột xuất');
    expect(fixture.nativeElement.querySelector('[data-testid="time-off-row"]')).toBeNull();
  });

  it('creates time off and shows the affected counts', () => {
    http.expectOne('/api/v1/admin/doctors').flush([doctor()]);
    http.expectOne('/api/v1/admin/doctor-time-off').flush([]);
    fixture.detectChanges();
    const component = fixture.componentInstance as any;
    component.selectedDoctorId.set('doctor-1');
    component.startDate.set('2026-08-10');
    component.endDate.set('2026-08-11');
    component.reason.set('Bác sĩ nghỉ đột xuất');
    (fixture.nativeElement.querySelector('button.erp-btn-primary') as HTMLButtonElement).click();
    fixture.detectChanges();
    (fixture.nativeElement.querySelector('[data-testid="save-time-off"]') as HTMLButtonElement).click();
    const request = http.expectOne('/api/v1/admin/doctor-time-off');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ doctorId: 'doctor-1', startDate: '2026-08-10', endDate: '2026-08-11', reason: 'Bác sĩ nghỉ đột xuất' });
    request.flush({ id: 'off-1', doctorId: 'doctor-1', doctorName: 'Bác sĩ Nguyễn An', startDate: '2026-08-10', endDate: '2026-08-11', reason: 'Bác sĩ nghỉ đột xuất', lockedSlotCount: 2, releasedHoldCount: 1, affectedAppointmentCount: 1, active: true });
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelectorAll('[data-testid="time-off-row"]').length).toBe(1);
    expect(fixture.nativeElement.textContent).toContain('2 khung giờ đã khóa');
  });
  it('hides the write form for administrators', () => {
    http.expectOne('/api/v1/admin/doctors').flush([doctor()]);
    http.expectOne('/api/v1/admin/doctor-time-off').flush([]);
    sessionStorage.setItem('clinicOneStaffRole', 'ADMIN');
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="save-time-off"]')).toBeNull();
    expect(fixture.nativeElement.textContent).not.toContain('Chế độ xem');
  });
});

function doctor() {
  return { staffId: 'doctor-1', username: 'doctor-an', fullName: 'Bác sĩ Nguyễn An', specialty: 'Khám Tổng Quát', roomId: 'room-1', roomCode: 'TQ-01', roomName: 'Phòng Tổng Quát 01', assigned: true, active: true };
}
