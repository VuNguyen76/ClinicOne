import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { DoctorManagement } from './doctor-management';

describe('DoctorManagement', () => {
  let fixture: ComponentFixture<DoctorManagement>;
  let http: HttpTestingController;

  beforeEach(async () => {
    sessionStorage.setItem('clinicOneAccessToken', 'staff-token');
    sessionStorage.setItem('clinicOneStaffRole', 'COORDINATOR');
    await TestBed.configureTestingModule({
      imports: [DoctorManagement],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(DoctorManagement);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  afterEach(() => {
    http.verify();
    sessionStorage.clear();
  });

  it('loads doctors, rooms and specialties for configuration', () => {
    http.expectOne('/api/v1/admin/doctors').flush([doctor('doctor-1', false)]);
    http.expectOne('/api/v1/rooms').flush([room('NOI-01')]);
    http.expectOne('/api/v1/specialties').flush([{ code: 'TQ', name: 'Khám Tổng Quát', description: '' }]);
    http.expectOne('/api/v1/admin/doctors/doctor-1/schedules').flush([]);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelectorAll('[data-testid="doctor-row"]').length).toBe(1);
    expect(fixture.nativeElement.textContent).toContain('Chưa phân công');
  });
});

function doctor(staffId: string, assigned: boolean) {
  return { staffId, username: 'doctor', fullName: 'Bác sĩ Nguyễn An', specialty: assigned ? 'Khám Tổng Quát' : null,
    roomId: assigned ? 'room-1' : null, roomCode: assigned ? 'NOI-01' : null, roomName: assigned ? 'Phòng Nội 01' : null,
    assigned, active: assigned };
}

function room(code: string) {
  return { id: 'room-1', code, name: 'Phòng Nội 01', specialty: 'Khám Tổng Quát', active: true };
}
