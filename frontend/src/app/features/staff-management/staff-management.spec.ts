import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { StaffManagement } from './staff-management';

describe('StaffManagement', () => {
  let fixture: ComponentFixture<StaffManagement>;
  let http: HttpTestingController;

  beforeEach(async () => {
    sessionStorage.setItem('clinicOneAccessToken', 'admin-token');
    sessionStorage.setItem('clinicOneStaffRole', 'ADMIN');
    await TestBed.configureTestingModule({
      imports: [StaffManagement],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(StaffManagement);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  afterEach(() => {
    http.verify();
    sessionStorage.clear();
  });

  it('shows staff accounts and their current status', () => {
    http.expectOne('/api/v1/admin/staff').flush([account('ACTIVE')]);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelectorAll('[data-testid="staff-row"]').length).toBe(1);
    expect(fixture.nativeElement.querySelector('[data-testid="staff-status"]').textContent).toContain('Đang hoạt động');
  });

  it('requires confirmation before locking and updates the row after confirmation', () => {
    http.expectOne('/api/v1/admin/staff').flush([account('ACTIVE')]);
    fixture.detectChanges();
    (fixture.nativeElement.querySelector('[data-testid="lock-staff"]') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[role="dialog"]')).not.toBeNull();

    (fixture.nativeElement.querySelector('[role="dialog"] button:last-child') as HTMLButtonElement).click();
    const request = http.expectOne('/api/v1/admin/staff/staff-1/lock');
    expect(request.request.method).toBe('POST');
    request.flush(account('LOCKED'));
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="staff-status"]').textContent).toContain('Đang khóa');
    expect(fixture.nativeElement.querySelector('[data-testid="unlock-staff"]').textContent).toContain('Mở khóa');
  });

  it('creates a staff account with employee identity and selected roles', () => {
    http.expectOne('/api/v1/admin/staff').flush([]);
    fixture.detectChanges();
    const component = fixture.componentInstance as any;
    component.openCreate();
    component.newFullName.set('Bác sĩ An');
    component.newEmployeeCode.set('NV001');
    component.newUnitName.set('ClinicOne');
    component.newDepartmentName.set('Khoa Nội');
    component.newRoles.set(['DOCTOR']);
    fixture.detectChanges();
    component.createAccount();
    const request = http.expectOne('/api/v1/admin/staff');
    expect(request.request.body).toEqual({
      fullName: 'Bác sĩ An', employeeCode: 'NV001', unitName: 'ClinicOne', departmentName: 'Khoa Nội', roles: ['DOCTOR'],
    });
    request.flush({ account: { ...account('ACTIVE'), username: 'NV001', employeeCode: 'NV001', roles: ['DOCTOR'] }, initialPassword: 'A1b2C3d4E5f6' });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('A1b2C3d4E5f6');
  });
});

function account(status: string) {
  return { staffId: 'staff-1', username: 'bs.an', fullName: 'Bác sĩ An', role: 'DOCTOR', status };
}
