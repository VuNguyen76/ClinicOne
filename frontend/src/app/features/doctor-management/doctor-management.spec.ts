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

  it('creates a doctor account when no doctor exists', () => {
    http.expectOne('/api/v1/admin/doctors').flush([]);
    http.expectOne('/api/v1/rooms').flush([room('NOI-01')]);
    http.expectOne('/api/v1/specialties').flush([]);
    fixture.detectChanges();

    fixture.nativeElement.querySelector('[data-testid="empty-create-doctor"]').click();
    fixture.detectChanges();
    (fixture.nativeElement.querySelector('[data-testid="doctor-username"]') as HTMLInputElement).value = 'bs.an';
    (fixture.nativeElement.querySelector('[data-testid="doctor-username"]') as HTMLInputElement).dispatchEvent(new Event('input'));
    (fixture.nativeElement.querySelector('[data-testid="doctor-full-name"]') as HTMLInputElement).value = 'Bác sĩ Nguyễn An';
    (fixture.nativeElement.querySelector('[data-testid="doctor-full-name"]') as HTMLInputElement).dispatchEvent(new Event('input'));
    (fixture.nativeElement.querySelector('[data-testid="doctor-password"]') as HTMLInputElement).value = 'doctor123';
    (fixture.nativeElement.querySelector('[data-testid="doctor-password"]') as HTMLInputElement).dispatchEvent(new Event('input'));
    fixture.nativeElement.querySelector('[data-testid="submit-create-doctor"]').click();

    const request = http.expectOne('/api/v1/admin/doctors');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ username: 'bs.an', fullName: 'Bác sĩ Nguyễn An', password: 'doctor123' });
    request.flush(doctor('doctor-1', false));
    http.expectOne('/api/v1/admin/doctors/doctor-1/schedules').flush([]);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelectorAll('[data-testid="doctor-row"]').length).toBe(1);
    expect(fixture.nativeElement.textContent).toContain('Bác sĩ Nguyễn An');
  });

  it('filters available rooms reactively based on the selected specialty in assignment drawer', () => {
    http.expectOne('/api/v1/admin/doctors').flush([doctor('doctor-1', true)]);
    http.expectOne('/api/v1/rooms').flush([
      { id: 'room-1', code: 'NOI-01', name: 'Phòng Nội 01', specialty: 'Khám Tổng Quát', active: true },
      { id: 'room-2', code: 'NHI-01', name: 'Phòng Nhi 01', specialty: 'Nhi Khoa', active: true },
    ]);
    http.expectOne('/api/v1/specialties').flush([
      { code: 'TQ', name: 'Khám Tổng Quát', description: '' },
      { code: 'NK', name: 'Nhi Khoa', description: '' },
    ]);
    http.expectOne('/api/v1/admin/doctors/doctor-1/schedules').flush([]);
    fixture.detectChanges();

    const component = fixture.componentInstance;
    expect(component['availableRooms']().length).toBe(1);
    expect(component['availableRooms']()[0].code).toBe('NOI-01');

    component['assignmentForm'].controls.specialty.setValue('Nhi Khoa');
    fixture.detectChanges();
    expect(component['availableRooms']().length).toBe(1);
    expect(component['availableRooms']()[0].code).toBe('NHI-01');
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
