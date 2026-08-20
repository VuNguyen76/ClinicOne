import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { Rescheduling } from './rescheduling';

describe('Rescheduling', () => {
  let fixture: ComponentFixture<Rescheduling>;
  let http: HttpTestingController;

  beforeEach(async () => {
    sessionStorage.setItem('clinicOneAccessToken', 'staff-token');
    sessionStorage.setItem('clinicOneStaffRole', 'COORDINATOR');
    await TestBed.configureTestingModule({
      imports: [Rescheduling],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(Rescheduling);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  afterEach(() => {
    http.verify();
    sessionStorage.clear();
  });

  it('loads the open cases and replacement slots for the selected case', () => {
    http.expectOne('/api/v1/admin/rescheduling').flush([rescheduleCase()]);
    const alternatives = http.expectOne('/api/v1/admin/rescheduling/case-1/alternatives');
    alternatives.flush([{
      specialty: 'Nội tổng quát', appointmentDate: '2026-08-12', startTime: '09:00:00',
      endTime: '10:00:00', doctorName: 'Bác sĩ Bình', remainingCapacity: 10,
      doctorId: 'doctor-2', roomCode: 'NOI-02',
    }]);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('CL-0001');
    expect(fixture.nativeElement.textContent).toContain('Bác sĩ Bình');
    expect(fixture.nativeElement.textContent).toContain('Khung giờ đang trống');
  });

  it('resolves a selected replacement slot', () => {
    http.expectOne('/api/v1/admin/rescheduling').flush([rescheduleCase()]);
    http.expectOne('/api/v1/admin/rescheduling/case-1/alternatives').flush([]);
    fixture.detectChanges();

    const component = fixture.componentInstance as any;
    component.form.setValue({
      appointmentDate: '2026-08-12', startTime: '09:00', doctorName: 'Bác sĩ Bình', doctorId: 'doctor-2',
    });
    fixture.detectChanges();
    (fixture.nativeElement.querySelector('form button[type="submit"]') as HTMLButtonElement).click();

    const request = http.expectOne('/api/v1/admin/rescheduling/case-1/resolve');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({
      appointmentDate: '2026-08-12', startTime: '09:00', doctorName: 'Bác sĩ Bình', doctorId: 'doctor-2',
    });
    request.flush({ ...rescheduleCase(), status: 'RESOLVED' });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Đã sắp xếp lại lịch CL-0001.');
  });

  it('keeps the resolution form hidden for administrators', () => {
    http.expectOne('/api/v1/admin/rescheduling').flush([rescheduleCase()]);
    http.expectOne('/api/v1/admin/rescheduling/case-1/alternatives').flush([]);
    sessionStorage.setItem('clinicOneStaffRole', 'ADMIN');
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('form')).toBeNull();
    expect(fixture.nativeElement.textContent).toContain('Chỉ Điều phối viên mới có thể xác nhận');
  });

  it('accurately computes pending cases for both OPEN and PENDING statuses', () => {
    http.expectOne('/api/v1/admin/rescheduling').flush([
      { ...rescheduleCase(), id: 'c-1', status: 'OPEN' },
      { ...rescheduleCase(), id: 'c-2', status: 'PENDING' },
      { ...rescheduleCase(), id: 'c-3', status: 'RESOLVED' },
    ]);
    http.expectOne('/api/v1/admin/rescheduling/c-1/alternatives').flush([]);
    fixture.detectChanges();

    const component = fixture.componentInstance as any;
    expect(component.pendingCasesCount()).toBe(2);
    expect(component.resolvedCasesCount()).toBe(1);
    expect(component.totalCasesCount()).toBe(3);
  });
});

function rescheduleCase() {
  return {
    id: 'case-1', appointmentId: 'appointment-1', appointmentCode: 'CL-0001', specialty: 'Nội tổng quát',
    oldDoctorName: 'Bác sĩ An', oldDoctorId: 'doctor-1', oldAppointmentDate: '2026-08-10',
    oldStartTime: '08:30:00', reason: 'Giờ làm của bác sĩ đã thay đổi.', status: 'OPEN',
    newDoctorName: null, newDoctorId: null, newAppointmentDate: null, newStartTime: null,
    createdAt: '2026-08-10T01:00:00Z', resolvedAt: null,
  };
}
