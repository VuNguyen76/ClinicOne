import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
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
    getMedicationUnits: vi.fn().mockReturnValue(of([
      { code: 'UNIT-VIEN', name: 'Viên', description: 'Dạng rắn: viên nén, viên nang', active: true, sortOrder: 1 },
      { code: 'UNIT-GOI', name: 'Gói', description: 'Dạng bột hoặc cốm pha', active: true, sortOrder: 2 },
    ])),
    getMedicationDosages: vi.fn().mockReturnValue(of([
      { code: 'DOS-GOI-2X', unitName: 'Gói', dosageFormat: '1 gói/lần x 2 lần/ngày (Sáng 1, Tối 1)', active: true, sortOrder: 1 },
      { code: 'DOS-VIEN-2X', unitName: 'Viên', dosageFormat: '1 viên/lần x 2 lần/ngày (Sáng 1, Tối 1)', active: true, sortOrder: 2 },
    ])),
    createMedicationUnit: vi.fn().mockReturnValue(of({ code: 'UNIT-CAP', name: 'Viên nang', description: '', active: true, sortOrder: 3 })),
    createMedicationDosage: vi.fn().mockReturnValue(of({ code: 'DOS-VIEN-1X', unitName: 'Viên', dosageFormat: '1 viên/lần/ngày', active: true, sortOrder: 3 })),
    updateMedicationUnit: vi.fn(),
    setMedicationUnitActive: vi.fn(),
    updateMedicationDosage: vi.fn(),
    setMedicationDosageActive: vi.fn(),
  };

  beforeEach(async () => {
    sessionStorage.setItem('clinicOneAccessToken', 'staff-token');
    sessionStorage.setItem('clinicOneStaffRoles', JSON.stringify(['COORDINATOR']));
    api.getAdminMedications.mockReturnValue(of([
      { id: 'med-1', code: 'PCM500', name: 'Paracetamol 500 mg', active: true, unitPrice: 5000 },
      { id: 'med-2', code: 'OLD-MED', name: 'Thuốc đã ngừng', active: false },
    ]));
    api.createMedication.mockReturnValue(of({ id: 'med-3', code: 'AMOX500', name: 'Amoxicillin 500 mg', active: true, unitPrice: 0 }));
    api.updateMedication.mockReturnValue(of({ id: 'med-1', code: 'PCM500', name: 'Paracetamol 500 mg', active: true }));
    api.setMedicationActive.mockReturnValue(of({ id: 'med-1', code: 'PCM500', name: 'Paracetamol 500 mg', active: false }));
    api.getMedicationUnits.mockReturnValue(of([
      { id: 'unit-1', code: 'UNIT-VIEN', name: 'Viên', description: 'Dạng rắn: viên nén, viên nang', active: true, sortOrder: 1 },
      { code: 'UNIT-GOI', name: 'Gói', description: 'Dạng bột hoặc cốm pha', active: true, sortOrder: 2 },
    ]));
    api.getMedicationDosages.mockReturnValue(of([
      { id: 'dosage-1', code: 'DOS-GOI-2X', unitName: 'Gói', dosageFormat: '1 gói/lần x 2 lần/ngày (Sáng 1, Tối 1)', active: true, sortOrder: 1 },
      { code: 'DOS-VIEN-2X', unitName: 'Viên', dosageFormat: '1 viên/lần x 2 lần/ngày (Sáng 1, Tối 1)', active: true, sortOrder: 2 },
    ]));
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

  it('switches between Medication table, Units table, and Dosages table', () => {
    // 1. Initially on medications tab
    expect(fixture.nativeElement.querySelector('[data-testid="medication-row"]')).toBeTruthy();

    // 2. Switch to Units tab
    (fixture.nativeElement.querySelector('[data-testid="tab-units"]') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[data-testid="units-table-container"]')).toBeTruthy();
    expect(fixture.nativeElement.textContent).toContain('UNIT-VIEN');

    // 3. Switch to Dosages tab
    (fixture.nativeElement.querySelector('[data-testid="tab-dosages"]') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[data-testid="dosages-table-container"]')).toBeTruthy();
    expect(fixture.nativeElement.textContent).toContain('DOS-GOI-2X');
  });

  it('does not show client-side catalog defaults when the database lists are empty', () => {
    api.getMedicationUnits.mockReturnValue(of([]));
    api.getMedicationDosages.mockReturnValue(of([]));
    const emptyFixture = TestBed.createComponent(MedicationCatalogManagement);
    emptyFixture.detectChanges();
    (emptyFixture.nativeElement.querySelector('[data-testid="tab-units"]') as HTMLButtonElement).click();
    emptyFixture.detectChanges();
    (emptyFixture.nativeElement.querySelector('[data-testid="tab-dosages"]') as HTMLButtonElement).click();
    emptyFixture.detectChanges();
    const component = emptyFixture.componentInstance as unknown as {
      medicationUnits: () => unknown[];
      medicationDosages: () => unknown[];
    };

    expect(component.medicationUnits()).toEqual([]);
    expect(component.medicationDosages()).toEqual([]);
    emptyFixture.destroy();
  });

  it('offers create actions for medication units and dosage presets to catalog managers', () => {
    (fixture.nativeElement.querySelector('[data-testid="tab-units"]') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[data-testid="open-create-medication-unit"]')).toBeTruthy();

    (fixture.nativeElement.querySelector('[data-testid="tab-dosages"]') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[data-testid="open-create-medication-dosage"]')).toBeTruthy();
  });

  it('lets an administrator view medication data without management actions', () => {
    fixture.destroy();
    sessionStorage.setItem('clinicOneStaffRoles', JSON.stringify(['ADMIN']));
    fixture = TestBed.createComponent(MedicationCatalogManagement);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Paracetamol 500 mg');
    expect(fixture.nativeElement.querySelector('[data-testid="open-create-medication"]')).toBeNull();
    expect(fixture.nativeElement.querySelector('[data-testid="edit-medication-med-1"]')).toBeNull();
    expect(fixture.nativeElement.querySelector('[data-testid="toggle-medication-med-1"]')).toBeNull();

    (fixture.nativeElement.querySelector('[data-testid="tab-units"]') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[data-testid="open-create-medication-unit"]')).toBeNull();
    expect(fixture.nativeElement.querySelector('[data-testid="edit-medication-unit-unit-1"]')).toBeNull();
  });

  it('matches medical-template tab typography and keeps the catalog tabs icon-free', () => {
    const tabs = ['tab-medications', 'tab-units', 'tab-dosages'];
    for (const tab of tabs) {
      const button = fixture.nativeElement.querySelector(`[data-testid="${tab}"]`) as HTMLElement;
      expect(button.className).toContain('text-xs');
      expect(button.className).toContain('px-4');
      expect(button.querySelector('mat-icon')).toBeNull();
    }
  });

  it('refreshes all three database-backed catalog lists from the refresh control', () => {
    api.getAdminMedications.mockClear();
    api.getMedicationUnits.mockClear();
    api.getMedicationDosages.mockClear();
    (fixture.nativeElement.querySelector('[title="Làm mới dữ liệu"]') as HTMLButtonElement).click();

    expect(api.getAdminMedications).toHaveBeenCalledTimes(1);
    expect(api.getMedicationUnits).toHaveBeenCalledWith(false);
    expect(api.getMedicationDosages).toHaveBeenCalledWith(undefined, false);
  });

  it('offers edit and safe deactivate/reactivate actions for units and dosage presets', () => {
    api.updateMedicationUnit.mockReturnValue(of({ id: 'unit-1', code: 'UNIT-VIEN', name: 'Viên nén', active: true }));
    api.setMedicationUnitActive.mockReturnValue(of({ id: 'unit-1', code: 'UNIT-VIEN', name: 'Viên', active: false }));
    api.updateMedicationDosage.mockReturnValue(of({ id: 'dosage-1', code: 'DOS-GOI-2X', unitName: 'Gói', dosageFormat: '2 gói/ngày', active: true }));
    api.setMedicationDosageActive.mockReturnValue(of({ id: 'dosage-1', code: 'DOS-GOI-2X', unitName: 'Gói', dosageFormat: '1 gói/lần x 2 lần/ngày', active: false }));

    (fixture.nativeElement.querySelector('[data-testid="tab-units"]') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[data-testid="edit-medication-unit-unit-1"]')).toBeTruthy();
    (fixture.nativeElement.querySelector('[data-testid="edit-medication-unit-unit-1"]') as HTMLButtonElement).click();
    fixture.detectChanges();
    const component = fixture.componentInstance;
    component['metadataName'].set('Viên nén');
    component['saveMetadata']();
    expect(api.updateMedicationUnit).toHaveBeenCalledWith('unit-1', expect.objectContaining({ name: 'Viên nén' }));
    (fixture.nativeElement.querySelector('[data-testid="toggle-medication-unit-unit-1"]') as HTMLButtonElement).click();
    expect(api.setMedicationUnitActive).toHaveBeenCalledWith('unit-1', false);

    (fixture.nativeElement.querySelector('[data-testid="tab-dosages"]') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[data-testid="edit-medication-dosage-dosage-1"]')).toBeTruthy();
    (fixture.nativeElement.querySelector('[data-testid="edit-medication-dosage-dosage-1"]') as HTMLButtonElement).click();
    fixture.detectChanges();
    component['metadataFormat'].set('2 gói/ngày');
    component['saveMetadata']();
    expect(api.updateMedicationDosage).toHaveBeenCalledWith('dosage-1', expect.objectContaining({ dosageFormat: '2 gói/ngày' }));
    (fixture.nativeElement.querySelector('[data-testid="toggle-medication-dosage-dosage-1"]') as HTMLButtonElement).click();
    expect(api.setMedicationDosageActive).toHaveBeenCalledWith('dosage-1', false);
  });

  it('creates medication metadata through the database-backed API', () => {
    (fixture.nativeElement.querySelector('[data-testid="tab-units"]') as HTMLButtonElement).click();
    fixture.detectChanges();
    (fixture.nativeElement.querySelector('[data-testid="open-create-medication-unit"]') as HTMLButtonElement).click();
    fixture.detectChanges();
    const component = fixture.componentInstance;
    component['metadataCode'].set('UNIT-CAP');
    component['metadataName'].set('Viên nang');
    component['saveMetadata']();
    expect(api.createMedicationUnit).toHaveBeenCalledWith({ code: 'UNIT-CAP', name: 'Viên nang', description: '', sortOrder: 3 });

    (fixture.nativeElement.querySelector('[data-testid="tab-dosages"]') as HTMLButtonElement).click();
    fixture.detectChanges();
    (fixture.nativeElement.querySelector('[data-testid="open-create-medication-dosage"]') as HTMLButtonElement).click();
    fixture.detectChanges();
    component['metadataCode'].set('DOS-VIEN-1X');
    component['metadataUnitName'].set('Viên');
    component['metadataFormat'].set('1 viên/lần/ngày');
    component['saveMetadata']();
    expect(api.createMedicationDosage).toHaveBeenCalledWith({
      code: 'DOS-VIEN-1X', unitName: 'Viên', dosageFormat: '1 viên/lần/ngày', description: '', sortOrder: 3,
    });
  });

  it('creates a medicine from the modal form without suggestion chips', () => {
    (fixture.nativeElement.querySelector('[data-testid="open-create-medication"]') as HTMLButtonElement).click();
    fixture.detectChanges();
    const modalText = fixture.nativeElement.querySelector('.erp-modal-container')?.textContent || '';
    expect(modalText).not.toContain('Gợi ý:');
    expect(modalText).not.toContain('Format mẫu chuẩn y tế:');
    expect(modalText).not.toContain('Mẫu cách dùng:');

    const component = fixture.componentInstance as unknown as {
      code: { set(value: string): void }; name: { set(value: string): void }; save(): void;
    };
    component.code.set('amox500');
    component.name.set('Amoxicillin 500 mg');
    component.save();

    expect(api.createMedication).toHaveBeenCalledWith('AMOX500', 'Amoxicillin 500 mg', {
      category: undefined,
      specialties: undefined,
      defaultDosage: undefined,
      defaultInstructions: undefined,
      unit: undefined,
      unitPrice: 0,
    });
  });

  it('shows and saves the configured medicine sale price', () => {
    (fixture.nativeElement.querySelector('[data-testid="open-create-medication"]') as HTMLButtonElement).click();
    fixture.detectChanges();
    const component = fixture.componentInstance;
    component['code'].set('AMOX500');
    component['name'].set('Amoxicillin 500 mg');
    component['unitPrice'].set(1250);
    component['save']();

    expect(api.createMedication).toHaveBeenCalledWith('AMOX500', 'Amoxicillin 500 mg', expect.objectContaining({ unitPrice: 1250 }));
  });

  it('places unit price after dosage and formats thousands with Vietnamese separators', () => {
    const table = fixture.nativeElement.querySelector('table') as HTMLTableElement;
    const headers = Array.from(table.querySelectorAll('thead th')).map((cell) => cell.textContent?.trim());
    const firstRowCells = Array.from(table.querySelectorAll('[data-testid="medication-row"] td'));

    expect(headers[3]).toBe('Liều dùng');
    expect(headers[4]).toBe('Đơn giá');
    expect(firstRowCells[4]?.textContent?.trim()).toContain('5.000');
    expect(firstRowCells[4]?.className).toContain('text-right');
  });

  it('rejects negative or fractional medicine sale prices before calling the API', () => {
    api.createMedication.mockClear();
    (fixture.nativeElement.querySelector('[data-testid="open-create-medication"]') as HTMLButtonElement).click();
    fixture.detectChanges();
    const component = fixture.componentInstance;
    component['code'].set('AMOX500');
    component['name'].set('Amoxicillin 500 mg');
    component['unitPrice'].set(-0.5);
    component['save']();

    expect(api.createMedication).not.toHaveBeenCalled();
    expect(component['error']()).toContain('Giá bán');
  });

  it('asks the API to suspend an active medicine instead of deleting it', () => {
    (fixture.nativeElement.querySelector('[data-testid="toggle-medication-med-1"]') as HTMLButtonElement).click();

    expect(api.setMedicationActive).toHaveBeenCalledWith('med-1', false);
  });

  it('renders accessible icon-only edit and remove actions across all catalog tabs', () => {
    const add = fixture.nativeElement.querySelector('[data-testid="open-create-medication"]') as HTMLButtonElement;
    expect(add.querySelector('mat-icon')?.textContent?.trim()).toBe('add');

    const medicationEdit = fixture.nativeElement.querySelector('[data-testid="edit-medication-med-1"]') as HTMLButtonElement;
    const medicationRemove = fixture.nativeElement.querySelector('[data-testid="toggle-medication-med-1"]') as HTMLButtonElement;
    expect(medicationEdit.children.length).toBe(1);
    expect(medicationEdit.firstElementChild?.tagName.toLowerCase()).toBe('mat-icon');
    expect(medicationEdit.getAttribute('aria-label')).toContain('Sửa');
    expect(medicationEdit.querySelector('mat-icon')?.textContent?.trim()).toBe('edit');
    expect(medicationRemove.children.length).toBe(1);
    expect(medicationRemove.firstElementChild?.tagName.toLowerCase()).toBe('mat-icon');
    expect(medicationRemove.getAttribute('aria-label')).toContain('Ngừng');
    expect(medicationRemove.querySelector('mat-icon')?.textContent?.trim()).toBe('delete_outline');

    (fixture.nativeElement.querySelector('[data-testid="tab-units"]') as HTMLButtonElement).click();
    fixture.detectChanges();
    const unitEdit = fixture.nativeElement.querySelector('[data-testid="edit-medication-unit-unit-1"]') as HTMLButtonElement;
    const unitRemove = fixture.nativeElement.querySelector('[data-testid="toggle-medication-unit-unit-1"]') as HTMLButtonElement;
    expect(unitEdit.children.length).toBe(1);
    expect(unitEdit.firstElementChild?.tagName.toLowerCase()).toBe('mat-icon');
    expect(unitEdit.querySelector('mat-icon')?.textContent?.trim()).toBe('edit');
    expect(unitRemove.children.length).toBe(1);
    expect(unitRemove.firstElementChild?.tagName.toLowerCase()).toBe('mat-icon');
    expect(unitRemove.querySelector('mat-icon')?.textContent?.trim()).toBe('delete_outline');

    (fixture.nativeElement.querySelector('[data-testid="tab-dosages"]') as HTMLButtonElement).click();
    fixture.detectChanges();
    const dosageEdit = fixture.nativeElement.querySelector('[data-testid="edit-medication-dosage-dosage-1"]') as HTMLButtonElement;
    const dosageRemove = fixture.nativeElement.querySelector('[data-testid="toggle-medication-dosage-dosage-1"]') as HTMLButtonElement;
    expect(dosageEdit.children.length).toBe(1);
    expect(dosageEdit.firstElementChild?.tagName.toLowerCase()).toBe('mat-icon');
    expect(dosageEdit.querySelector('mat-icon')?.textContent?.trim()).toBe('edit');
    expect(dosageRemove.children.length).toBe(1);
    expect(dosageRemove.firstElementChild?.tagName.toLowerCase()).toBe('mat-icon');
    expect(dosageRemove.querySelector('mat-icon')?.textContent?.trim()).toBe('delete_outline');
  });

  it('updates a medicine through the edit action API', () => {
    (fixture.nativeElement.querySelector('[data-testid="edit-medication-med-1"]') as HTMLButtonElement).click();
    fixture.detectChanges();
    const component = fixture.componentInstance;
    component['name'].set('Paracetamol 500 mg - cập nhật');
    component['save']();

    expect(api.updateMedication).toHaveBeenCalledWith('med-1', 'PCM500', 'Paracetamol 500 mg - cập nhật', expect.any(Object));
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
      unitPrice: 0,
    });
  });

  it('does not offer a dosage preset belonging to a different unit', () => {
    const component = fixture.componentInstance;
    component['selectUnit']('Chai');
    expect(component['standardDosageFormats']()).toEqual([]);
  });
});
