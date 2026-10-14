import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { vi } from 'vitest';
import { AuthApiService } from '../../core/auth/auth-api.service';
import { ReasonCatalogManagement } from './reason-catalog-management';

describe('ReasonCatalogManagement', () => {
  let fixture: ComponentFixture<ReasonCatalogManagement>;
  const api = {
    getClinicalReasons: vi.fn().mockReturnValue(of([
      { id: 'clinical-1', specialtyCode: 'CARD', specialtyName: 'Tim mạch', symptomName: 'Đau ngực', sortOrder: 1, active: true },
    ])),
    getSpecialties: vi.fn().mockReturnValue(of([{ code: 'CARD', name: 'Tim mạch', description: '' }])),
    createClinicalReason: vi.fn(),
    updateClinicalReason: vi.fn(),
    toggleClinicalReasonActive: vi.fn(),
    deleteClinicalReason: vi.fn(),
    getAdminCancellationReasons: vi.fn(),
    createCancellationReason: vi.fn(),
    updateCancellationReason: vi.fn(),
    setCancellationReasonActive: vi.fn(),
  } as unknown as Pick<AuthApiService, 'getClinicalReasons' | 'getSpecialties' | 'createClinicalReason' | 'updateClinicalReason'
    | 'toggleClinicalReasonActive' | 'deleteClinicalReason' | 'getAdminCancellationReasons' | 'createCancellationReason'
    | 'updateCancellationReason' | 'setCancellationReasonActive'>;

  beforeEach(async () => {
    sessionStorage.setItem('clinicOneStaffRole', 'ADMIN');
    sessionStorage.setItem('clinicOneStaffRoles', JSON.stringify(['ADMIN']));
    vi.mocked(api.getAdminCancellationReasons).mockReturnValue(of([
      { id: 'reason-1', type: 'APPOINTMENT_CANCELLATION', code: 'SCHEDULE_CHANGE', label: 'Thay đổi kế hoạch', active: true },
      { id: 'reason-2', type: 'APPOINTMENT_CANCELLATION', code: 'MEDICAL_EMERGENCY', label: 'Có việc đột xuất', active: true },
      { id: 'reason-3', type: 'APPOINTMENT_CANCELLATION', code: 'FOUND_OTHER_PROVIDER', label: 'Đã khám ở nơi khác', active: true },
    ]));
    await TestBed.configureTestingModule({
      imports: [ReasonCatalogManagement],
      providers: [provideRouter([]), { provide: AuthApiService, useValue: api }],
    }).compileComponents();
    fixture = TestBed.createComponent(ReasonCatalogManagement);
    fixture.detectChanges();
  });

  it('shows the seeded cancellation reasons', () => {
    expect(fixture.nativeElement.textContent).toContain('Thay đổi kế hoạch');
    expect(fixture.nativeElement.textContent).toContain('tối thiểu 3 lý do');
  });

  it('creates a new reason through the admin API', () => {
    vi.mocked(api.createCancellationReason).mockReturnValue(of({
      id: 'reason-4', type: 'APPOINTMENT_CANCELLATION', code: 'TRAVEL', label: 'Đi công tác', active: true,
    }));
    (fixture.nativeElement.querySelector('button.erp-btn-primary') as HTMLButtonElement).click();
    fixture.detectChanges();
    const component = fixture.componentInstance as any;
    component.code.set('TRAVEL');
    component.label.set('Đi công tác');
    (fixture.nativeElement.querySelector('button[type="submit"]') as HTMLButtonElement).click();
    expect(api.createCancellationReason).toHaveBeenCalledWith('TRAVEL', 'Đi công tác');
  });

  it('allows editing a reason and saves changes through the admin API', () => {
    vi.mocked(api.updateCancellationReason).mockReturnValue(of({
      id: 'reason-1', type: 'APPOINTMENT_CANCELLATION', code: 'SCHEDULE_CHANGE', label: 'Đổi lịch hẹn', active: true,
    }));
    expect(fixture.nativeElement.querySelector('[data-testid="edit-reason-reason-1"]')).toBeTruthy();
    (fixture.nativeElement.querySelector('[data-testid="edit-reason-reason-1"]') as HTMLButtonElement).click();
    fixture.detectChanges();
    const component = fixture.componentInstance as any;
    component.label.set('Đổi lịch hẹn');
    (fixture.nativeElement.querySelector('button[type="submit"]') as HTMLButtonElement).click();
    expect(api.updateCancellationReason).toHaveBeenCalledWith('reason-1', 'SCHEDULE_CHANGE', 'Đổi lịch hẹn');
  });

  it('renders icon-only edit and remove controls and removes through the active-state API', () => {
    vi.mocked(api.setCancellationReasonActive).mockReturnValue(of({
      id: 'reason-1', type: 'APPOINTMENT_CANCELLATION', code: 'SCHEDULE_CHANGE', label: 'Thay đổi kế hoạch', active: false,
    }));
    const edit = fixture.nativeElement.querySelector('[data-testid="edit-reason-reason-1"]') as HTMLButtonElement;
    const remove = fixture.nativeElement.querySelector('[data-testid="toggle-reason-reason-1"]') as HTMLButtonElement;
    expect(edit.children.length).toBe(1);
    expect(edit.firstElementChild?.tagName.toLowerCase()).toBe('mat-icon');
    expect(edit.querySelector('mat-icon')?.textContent?.trim()).toBe('edit');
    expect(edit.getAttribute('aria-label')).toContain('Sửa');
    expect(remove.children.length).toBe(1);
    expect(remove.firstElementChild?.tagName.toLowerCase()).toBe('mat-icon');
    expect(remove.querySelector('mat-icon')?.textContent?.trim()).toBe('delete_outline');
    expect(remove.getAttribute('aria-label')).toContain('Ngừng');

    remove.click();
    expect(api.setCancellationReasonActive).toHaveBeenCalledWith('reason-1', false);
  });

  it('lets administrators view clinical reasons but not manage them', () => {
    fixture.componentInstance['switchTab']('clinical');
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Đau ngực');
    expect(fixture.nativeElement.querySelector('[data-testid="create-clinical-reason"]')).toBeNull();
    expect(fixture.nativeElement.querySelector('button[title="Chỉnh sửa lý do"]')).toBeNull();
    expect(fixture.nativeElement.querySelector('th')?.parentElement?.textContent).not.toContain('Thao tác');

    fixture.componentInstance['openCreateClinical']();
    expect(fixture.componentInstance['clinicalModalOpen']()).toBe(false);
    expect(api.createClinicalReason).not.toHaveBeenCalled();
  });

  it('lets coordinators view cancellation reasons without edit controls and manage clinical reasons', () => {
    fixture.destroy();
    sessionStorage.setItem('clinicOneStaffRole', 'COORDINATOR');
    sessionStorage.setItem('clinicOneStaffRoles', JSON.stringify(['COORDINATOR']));
    vi.mocked(api.getAdminCancellationReasons).mockClear();
    fixture = TestBed.createComponent(ReasonCatalogManagement);
    fixture.detectChanges();

    expect(fixture.componentInstance['activeTab']()).toBe('clinical');
    expect(fixture.nativeElement.querySelector('[data-testid="create-clinical-reason"]')).toBeTruthy();
    fixture.componentInstance['switchTab']('cancellation');
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Thay đổi kế hoạch');
    expect(fixture.nativeElement.querySelector('[data-testid="edit-reason-reason-1"]')).toBeNull();
    expect(api.getAdminCancellationReasons).toHaveBeenCalledOnce();
  });
});
