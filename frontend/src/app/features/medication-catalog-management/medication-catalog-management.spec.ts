import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { vi } from 'vitest';
import { AuthApiService } from '../../core/auth/auth-api.service';
import { MedicationCatalogManagement } from './medication-catalog-management';

describe('MedicationCatalogManagement', () => {
  let fixture: ComponentFixture<MedicationCatalogManagement>;
  const api = {
    getAdminMedications: vi.fn(),
    createMedication: vi.fn(),
    updateMedication: vi.fn(),
    setMedicationActive: vi.fn(),
    getSpecialties: vi.fn().mockReturnValue(of([])),
  };

  beforeEach(async () => {
    sessionStorage.setItem('clinicOneAccessToken', 'staff-token');
    sessionStorage.setItem('clinicOneStaffRoles', JSON.stringify(['ADMIN']));
    api.getAdminMedications.mockReturnValue(of([
      { id: 'med-1', code: 'PCM500', name: 'Paracetamol 500 mg', active: true },
      { id: 'med-2', code: 'OLD-MED', name: 'Thuốc đã ngừng', active: false },
    ]));
    api.createMedication.mockReturnValue(of({ id: 'med-3', code: 'AMOX500', name: 'Amoxicillin 500 mg', active: true }));
    api.updateMedication.mockReturnValue(of({ id: 'med-1', code: 'PCM500', name: 'Paracetamol 500 mg', active: true }));
    api.setMedicationActive.mockReturnValue(of({ id: 'med-1', code: 'PCM500', name: 'Paracetamol 500 mg', active: false }));
    await TestBed.configureTestingModule({
      imports: [MedicationCatalogManagement],
      providers: [provideRouter([]), { provide: AuthApiService, useValue: api }],
    }).compileComponents();
    fixture = TestBed.createComponent(MedicationCatalogManagement);
    fixture.detectChanges();
  });

  afterEach(() => {
    TestBed.resetTestingModule();
    sessionStorage.clear();
  });

  it('lists active and inactive medicines separately for safe operation', () => {
    const text = fixture.nativeElement.textContent;
    expect(text).toContain('Paracetamol 500 mg');
    expect(text).toContain('Đang dùng');
    expect(text).toContain('Tạm ngưng');
    expect(fixture.nativeElement.querySelector('[data-testid="staff-workspace-shell"]')).toBeTruthy();
  });

  it('creates a medicine from the modal form', () => {
    (fixture.nativeElement.querySelector('[data-testid="open-create-medication"]') as HTMLButtonElement).click();
    fixture.detectChanges();
    const component = fixture.componentInstance as unknown as {
      code: { set(value: string): void }; name: { set(value: string): void }; save(): void;
    };
    component.code.set('amox500');
    component.name.set('Amoxicillin 500 mg');
    component.save();

    expect(api.createMedication).toHaveBeenCalledWith('AMOX500', 'Amoxicillin 500 mg');
  });

  it('asks the API to suspend an active medicine instead of deleting it', () => {
    (fixture.nativeElement.querySelector('[data-testid="toggle-medication-med-1"]') as HTMLButtonElement).click();

    expect(api.setMedicationActive).toHaveBeenCalledWith('med-1', false);
  });

  it('creates a medicine with clinical presets (unit, specialty select, and standard dosage)', () => {
    (fixture.nativeElement.querySelector('[data-testid="open-create-medication"]') as HTMLButtonElement).click();
    fixture.detectChanges();
    const component = fixture.componentInstance;

    // 1. Select unit preset
    component['selectUnit']('Gói');
    fixture.detectChanges();
    expect(component['unit']()).toBe('Gói');

    // 2. Formats adapt dynamically to "Gói"
    const dosageFormats = component['standardDosageFormats']();
    expect(dosageFormats.some((fmt) => fmt.includes('gói/lần'))).toBe(true);

    // 3. Select standard dosage preset
    component['selectDosage'](dosageFormats[0]);
    expect(component['defaultDosage']()).toBe(dosageFormats[0]);

    // 4. Select specialty from dropdown
    component['specialties'].set('Khám Tổng Quát');

    // 5. Select category preset
    component['selectCategory']('Hạ sốt & Giảm đau');

    component['code'].set('eff-para-250');
    component['name'].set('Efferalgan 250mg');
    component['save']();

    expect(api.createMedication).toHaveBeenCalledWith('EFF-PARA-250', 'Efferalgan 250mg', {
      unit: 'Gói',
      specialties: 'Khám Tổng Quát',
      defaultDosage: dosageFormats[0],
      category: 'Hạ sốt & Giảm đau',
      defaultInstructions: undefined,
    });
  });
});
