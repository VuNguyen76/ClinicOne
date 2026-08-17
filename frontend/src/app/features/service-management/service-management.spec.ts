import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { ServiceManagement } from './service-management';

describe('ServiceManagement', () => {
  let fixture: ComponentFixture<ServiceManagement>;
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ServiceManagement],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();

    sessionStorage.setItem('clinicOneSessionType', 'STAFF');
    sessionStorage.setItem('clinicOneStaffRoles', JSON.stringify(['COORDINATOR']));
    fixture = TestBed.createComponent(ServiceManagement);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  afterEach(() => { http.verify(); sessionStorage.clear(); });

  it('loads services, doctors and specialties for the coordinator workspace', () => {
    http.expectOne('/api/v1/admin/services').flush([service('s-1', true)]);
    http.expectOne('/api/v1/admin/doctors').flush([doctor('d-1')]);
    http.expectOne('/api/v1/specialties').flush([{ code: 'TQ', name: 'Khám Tổng Quát', description: '' }]);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelectorAll('[data-testid="service-row"]').length).toBe(1);
    expect(fixture.nativeElement.textContent).toContain('Khám tổng quát cơ bản');
  });

  it('creates a service with selected eligible doctor', () => {
    http.expectOne('/api/v1/admin/services').flush([]);
    http.expectOne('/api/v1/admin/doctors').flush([doctor('d-1')]);
    http.expectOne('/api/v1/specialties').flush([{ code: 'TQ', name: 'Khám Tổng Quát', description: '' }]);
    fixture.detectChanges();

    (fixture.nativeElement.querySelector('[data-testid="new-service"]') as HTMLButtonElement).click();
    fixture.detectChanges();
    const component = fixture.componentInstance as any;
    component.name.set('Khám tổng quát cơ bản');
    component.specialty.set('Khám Tổng Quát');
    component.visitType.set('Khám thường');
    component.selectedDoctorIds.set(['d-1']);
    fixture.detectChanges();

    (fixture.nativeElement.querySelector('[data-testid="save-service"]') as HTMLButtonElement).click();
    const request = http.expectOne('/api/v1/admin/services');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({
      name: 'Khám tổng quát cơ bản',
      specialty: 'Khám Tổng Quát',
      visitType: 'Khám thường',
      durationMinutes: 30,
      doctorIds: ['d-1'],
      requiresMedicalRecord: true,
    });
    request.flush(service('s-1', true));
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[role="dialog"]')).toBeNull();
  });

  it('shows the service catalog as read-only to an admin', () => {
    sessionStorage.setItem('clinicOneStaffRoles', JSON.stringify(['ADMIN']));
    http.expectOne('/api/v1/admin/services').flush([service('s-1', true)]);
    http.expectOne('/api/v1/admin/doctors').flush([doctor('d-1')]);
    http.expectOne('/api/v1/specialties').flush([]);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="new-service"]')).toBeNull();
    expect(fixture.nativeElement.textContent).not.toContain('Chế độ xem');
  });
});

function doctor(id: string) {
  return { staffId: id, username: `doctor-${id}`, fullName: 'Bác sĩ Nguyễn An', specialty: 'Khám Tổng Quát',
    roomId: 'room-1', roomCode: 'TQ-01', roomName: 'Phòng Tổng Quát 01', assigned: true, active: true };
}

function service(id: string, active: boolean) {
  return { id, name: 'Khám tổng quát cơ bản', specialty: 'Khám Tổng Quát', visitType: 'Khám thường', durationMinutes: 30,
    active, eligibleDoctors: [{ doctorProfileId: 'profile-1', staffId: 'd-1', fullName: 'Bác sĩ Nguyễn An' }] };
}
