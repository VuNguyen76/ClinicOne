import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { vi } from 'vitest';
import { MedicalRecordTemplateManagement } from './medical-record-template-management';

describe('MedicalRecordTemplateManagement', () => {
  let fixture: ComponentFixture<MedicalRecordTemplateManagement>;
  let http: HttpTestingController;

  beforeEach(async () => {
    sessionStorage.setItem('clinicOneAccessToken', 'admin-token');
    sessionStorage.setItem('clinicOneStaffRole', 'COORDINATOR');
    sessionStorage.setItem('clinicOneStaffRoles', JSON.stringify(['COORDINATOR']));
    await TestBed.configureTestingModule({
      imports: [MedicalRecordTemplateManagement],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();
    fixture = TestBed.createComponent(MedicalRecordTemplateManagement);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  afterEach(() => {
    http.verify();
    sessionStorage.clear();
    vi.restoreAllMocks();
    TestBed.resetTestingModule();
  });

  it('loads the ERP master list with specialty and service choices', () => {
    flushReferenceData();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="staff-workspace-shell"]')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('[workspace-tabs]')?.textContent).toContain('Thiết lập mẫu');
    expect(fixture.nativeElement.querySelector('[data-testid="template-row-0"]')?.textContent)
      .toContain('Khám nội tổng quát');
    expect((fixture.nativeElement.querySelector('[data-testid="template-specialty"]') as HTMLSelectElement).options.length)
      .toBe(2);
    expect(fixture.nativeElement.querySelector('#diagnosis-template-catalog option')?.getAttribute('label'))
      .toContain('Z00.0');
    fixture.componentInstance['addPrescriptionLine']();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('#medication-template-catalog option')?.getAttribute('label'))
      .toContain('MED-OMEP');
  });

  it('lets administrators view medical templates and diagnoses without edit controls', () => {
    flushReferenceData();
    sessionStorage.setItem('clinicOneStaffRole', 'ADMIN');
    sessionStorage.setItem('clinicOneStaffRoles', JSON.stringify(['ADMIN']));
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="template-row-0"]')?.textContent).toContain('Khám nội tổng quát');
    expect(fixture.nativeElement.querySelector('[data-testid="add-template-btn"]')).toBeNull();
    expect(fixture.nativeElement.querySelector('[workspace-tabs]')?.textContent).not.toContain('Thiết lập mẫu');
    expect(fixture.nativeElement.querySelector('[data-testid="tab-diagnoses"]')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('[data-testid="edit-template-template-1"]')).toBeNull();

    (fixture.nativeElement.querySelector('[data-testid="tab-diagnoses"]') as HTMLButtonElement).click();
    fixture.detectChanges();
    http.expectOne((request) => request.url === '/api/v1/admin/diagnoses').flush([
      { id: 'dx-view', code: 'I10', name: 'Tăng huyết áp', active: true },
    ]);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[data-testid="embedded-diagnosis-catalog"]')?.textContent).toContain('Tăng huyết áp');
    expect(fixture.nativeElement.querySelector('[data-testid="open-create-diagnosis"]')).toBeNull();
    expect(fixture.nativeElement.querySelector('[data-testid="edit-diagnosis-dx-view"]')).toBeNull();
  });

  it('presents template setup as three clearly separated full-width sections', () => {
    flushReferenceData();
    fixture.detectChanges();

    const sections = fixture.nativeElement.querySelector('[data-testid="template-form-sections"]');
    const identity = fixture.nativeElement.querySelector('[data-testid="template-identity-section"]');
    const clinical = fixture.nativeElement.querySelector('[data-testid="template-clinical-section"]');
    const medications = fixture.nativeElement.querySelector('[data-testid="template-medications-section"]');

    expect(sections).toBeTruthy();
    expect([identity, clinical, medications].every((section) => section?.parentElement === sections)).toBe(true);
  });

  it('navigates the setup sections with a three-step flow like the examination screen', () => {
    flushReferenceData();
    fixture.detectChanges();

    const stepper = fixture.nativeElement.querySelector('[data-testid="template-setup-stepper"]');
    expect(stepper.querySelectorAll('button[data-testid^="template-step-"]').length).toBe(3);
    expect(fixture.nativeElement.querySelector('[data-testid="template-identity-section"]').classList.contains('hidden')).toBe(false);

    (fixture.nativeElement.querySelector('[data-testid="template-step-2"]') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[data-testid="template-identity-section"]').classList.contains('hidden')).toBe(true);
    expect(fixture.nativeElement.querySelector('[data-testid="template-clinical-section"]').classList.contains('hidden')).toBe(false);

    (fixture.nativeElement.querySelector('[data-testid="template-step-next"]') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[data-testid="template-medications-section"]').classList.contains('hidden')).toBe(false);
  });

  it('opens the existing diagnosis catalog inside the template tabs without a nested workspace shell', () => {
    flushReferenceData();
    fixture.detectChanges();

    const diagnosisTab = fixture.nativeElement.querySelector('[data-testid="tab-diagnoses"]') as HTMLButtonElement;
    expect(diagnosisTab).toBeTruthy();
    diagnosisTab.click();
    fixture.detectChanges();

    const catalogRequest = http.expectOne((candidate) =>
      candidate.url === '/api/v1/admin/diagnoses' && candidate.params.get('activeOnly') === 'false');
    catalogRequest.flush([{ id: 'dx-active', code: 'I10', name: 'Tăng huyết áp', active: true }]);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="embedded-diagnosis-catalog"]')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('[data-testid="embedded-diagnosis-catalog"]')?.textContent).toContain('Tăng huyết áp');
    expect(fixture.nativeElement.querySelectorAll('[data-testid="staff-workspace-shell"]').length).toBe(1);
  });

  it('creates a template from readable clinical fields instead of raw technical syntax', () => {
    flushReferenceData([]);
    fixture.detectChanges();
    setValue('template-code', 'NOI-TQ');
    setValue('template-name', 'Khám nội tổng quát');
    setValue('template-specialty', 'Nội tổng quát');
    fixture.detectChanges();
    setValue('template-notes', 'Khám theo trình tự nội tổng quát.');
    setValue('template-plan', 'Tư vấn chăm sóc và tái khám khi cần.');
    setValue('template-diagnosis', 'Khám sức khỏe tổng quát');
    fixture.detectChanges();
    goToMedicationStep();

    (fixture.nativeElement.querySelector('[data-testid="save-medical-template"]') as HTMLButtonElement).click();
    const request = http.expectOne('/api/v1/medical-record-templates');
    expect(request.request.method).toBe('POST');
    expect(JSON.parse(request.request.body.fieldDefinition)).toEqual({
      diagnosis: 'Khám sức khỏe tổng quát',
      examinationNotes: 'Khám theo trình tự nội tổng quát.',
      treatmentPlan: 'Tư vấn chăm sóc và tái khám khi cần.',
    });
    request.flush(template());
    http.expectOne((candidate) => candidate.url === '/api/v1/medical-record-templates').flush([]);
  });

  it('uses icon actions for editing and safely removing an existing template through the APIs', () => {
    flushReferenceData();
    fixture.detectChanges();

    const editButton = fixture.nativeElement.querySelector('[data-testid="edit-template-template-1"]') as HTMLButtonElement;
    const deleteButton = fixture.nativeElement.querySelector('[data-testid="delete-template-template-1"]') as HTMLButtonElement;
    expect(editButton.querySelector('mat-icon')?.textContent?.trim()).toBe('edit');
    expect(editButton.getAttribute('aria-label')).toContain('Sửa');
    expect(deleteButton.querySelector('mat-icon')?.textContent?.trim()).toBe('delete_outline');
    expect(deleteButton.getAttribute('aria-label')).toContain('Xóa');

    editButton.click();
    fixture.detectChanges();
    fixture.componentInstance['name'].set('Khám nội tổng quát cập nhật');
    fixture.componentInstance['save']();
    const update = http.expectOne('/api/v1/medical-record-templates/template-1');
    expect(update.request.method).toBe('PUT');
    update.flush(template());
    http.expectOne((request) => request.url === '/api/v1/medical-record-templates'
      && request.params.get('activeOnly') === 'true').flush([template()]);
    fixture.detectChanges();

    vi.spyOn(window, 'confirm').mockReturnValue(true);
    (fixture.nativeElement.querySelector('[data-testid="delete-template-template-1"]') as HTMLButtonElement).click();
    const remove = http.expectOne('/api/v1/medical-record-templates/template-1');
    expect(remove.request.method).toBe('DELETE');
    remove.flush(null);
    http.expectOne((request) => request.url === '/api/v1/medical-record-templates'
      && request.params.get('activeOnly') === 'true').flush([]);
  });

  it('fills every medication field from the catalog and replaces stale values when another drug is chosen', () => {
    flushReferenceData([]);
    fixture.detectChanges();
    setValue('template-code', 'NOI-TQ-2');
    setValue('template-name', 'Khám dạ dày kèm đơn thuốc');
    setValue('template-specialty', 'Nội tổng quát');
    fixture.detectChanges();
    setValue('template-notes', 'Đau thượng vị');
    fixture.detectChanges();

    fixture.componentInstance['addPrescriptionLine']();
    fixture.detectChanges();
    fixture.componentInstance['updateLineField'](0, 'dosage', 'old dosage');
    fixture.componentInstance['updateLineField'](0, 'quantity', 9);
    fixture.componentInstance['updateLineField'](0, 'unit', 'old unit');
    fixture.componentInstance['updateLineField'](0, 'instructions', 'old instructions');
    fixture.componentInstance['updateMedicationLine'](0, 'Omeprazole 20mg');
    expect(fixture.componentInstance['prescriptionLines']()[0]).toMatchObject({
      medicationId: '11111111-1111-4111-8111-111111111111',
      dosage: '1 viên/ngày',
      quantity: 1,
      unit: 'Viên',
      instructions: 'Uống trước ăn',
    });
    fixture.componentInstance['updateLineField'](0, 'dosage', '20mg x 2 lần/ngày');
    fixture.componentInstance['updateLineField'](0, 'quantity', 28);
    fixture.componentInstance['updateLineField'](0, 'instructions', 'Uống trước ăn');
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="template-prescription-line-0"]')?.tagName).toBe('ARTICLE');
    goToMedicationStep();

    (fixture.nativeElement.querySelector('[data-testid="save-medical-template"]') as HTMLButtonElement).click();
    const request = http.expectOne('/api/v1/medical-record-templates');
    expect(request.request.method).toBe('POST');
    const savedContent = JSON.parse(request.request.body.fieldDefinition);
    expect(savedContent.examinationNotes).toBe('Đau thượng vị');
    expect(savedContent.prescriptionLines).toBeDefined();
    expect(savedContent.prescriptionLines.length).toBe(1);
    expect(savedContent.prescriptionLines[0].medicationName).toBe('Omeprazole 20mg');
    expect(savedContent.prescriptionLines[0].medicationId).toBe('11111111-1111-4111-8111-111111111111');
    expect(savedContent.prescriptionLines[0].dosage).toBe('20mg x 2 lần/ngày');
    expect(savedContent.prescriptionLines[0].unit).toBe('Viên');
    request.flush(template());
    http.expectOne((candidate) => candidate.url === '/api/v1/medical-record-templates').flush([]);
  });

  it('clears unavailable catalog defaults instead of retaining the previous medication values', () => {
    flushReferenceData([]);
    fixture.componentInstance['addPrescriptionLine']();
    fixture.detectChanges();
    fixture.componentInstance['updateMedicationLine'](0, 'Omeprazole 20mg');
    fixture.componentInstance['updateLineField'](0, 'dosage', 'custom dosage');
    fixture.componentInstance['updateLineField'](0, 'quantity', 12);
    fixture.componentInstance['updateLineField'](0, 'instructions', 'custom instructions');

    fixture.componentInstance['updateMedicationLine'](0, 'Ibuprofen 200mg');

    expect(fixture.componentInstance['prescriptionLines']()[0]).toMatchObject({
      medicationId: '22222222-2222-4222-8222-222222222222',
      medicationName: 'Ibuprofen 200mg',
      dosage: '',
      quantity: 1,
      unit: '',
      instructions: '',
    });
  });

  it('shows template management as read-only view mode for DOCTOR role', () => {
    sessionStorage.setItem('clinicOneStaffRole', 'DOCTOR');
    sessionStorage.setItem('clinicOneStaffRoles', JSON.stringify(['DOCTOR']));

    const tplWithPrescription = {
      ...template(),
      fieldDefinition: JSON.stringify({
        reason: 'Khám kiểm tra',
        examinationNotes: 'Bụng mềm, không điểm đau khu trú',
        diagnosis: 'Viêm dạ dày cấp',
        prescriptionLines: [
          { medicationName: 'Phosphalugel', dosage: '1 gói x 2 lần', quantity: 14, unit: 'Gói', instructions: 'Uống khi đau' }
        ]
      })
    };
    flushReferenceData([tplWithPrescription], false);
    fixture.detectChanges();

    // DOCTOR cannot see "Thiết lập mẫu" tab
    expect(fixture.nativeElement.querySelector('[workspace-tabs]')?.textContent).not.toContain('Thiết lập mẫu');
    // DOCTOR cannot see "+ Thêm mẫu phiếu" button
    expect(fixture.nativeElement.querySelector('[data-testid="add-template-btn"]')).toBeNull();

    // DOCTOR sees "Xem" button instead of Edit / Delete buttons
    const row = fixture.nativeElement.querySelector('[data-testid="template-row-0"]');
    expect(row.textContent).toContain('Xem');
    expect(row.querySelector('button[title="Sửa mẫu"]')).toBeNull();
    expect(row.querySelector('button[title="Xóa mẫu"]')).toBeNull();

    // Clicking "Xem" opens read-only detail modal
    const viewBtn = Array.from(row.querySelectorAll('button')).find((b: any) => b.textContent.includes('Xem')) as HTMLButtonElement;
    viewBtn.click();
    fixture.detectChanges();

    const modal = fixture.nativeElement.querySelector('[role="dialog"]');
    expect(modal).toBeTruthy();
    expect(modal.textContent).toContain('Phosphalugel');
    expect(modal.textContent).toContain('Viêm dạ dày cấp');

    // Clicking close button dismisses modal
    const closeBtn = modal.querySelector('button[aria-label="Đóng"]') as HTMLButtonElement;
    closeBtn.click();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[role="dialog"]')).toBeNull();
  });

  function flushReferenceData(templates = [template()], includeCatalogs = true): void {
    http.expectOne((candidate) => candidate.url === '/api/v1/medical-record-templates').flush(templates);
    http.expectOne((candidate) => candidate.url === '/api/v1/specialties').flush([
      { code: 'NOI_TONG_QUAT', name: 'Nội tổng quát', description: '' },
    ]);
    http.expectOne('/api/v1/services').flush([
      { id: 'service-1', name: 'Khám nội tổng quát', specialty: 'Nội tổng quát', visitType: 'STANDARD', durationMinutes: 30, active: true, eligibleDoctors: [] },
    ]);
    if (!includeCatalogs) return;
    const diagnosesRequest = http.expectOne((candidate) => candidate.url === '/api/v1/admin/diagnoses');
    expect(diagnosesRequest.request.params.get('activeOnly')).toBe('true');
    diagnosesRequest.flush([{ id: 'dx-1', code: 'Z00.0', name: 'Khám sức khỏe tổng quát', active: true }]);
    const medicationsRequest = http.expectOne((candidate) => candidate.url === '/api/v1/admin/medications');
    expect(medicationsRequest.request.params.get('activeOnly')).toBe('true');
    medicationsRequest.flush([{
      id: '11111111-1111-4111-8111-111111111111', code: 'MED-OMEP', name: 'Omeprazole 20mg', active: true,
      defaultDosage: '1 viên/ngày', defaultInstructions: 'Uống trước ăn', unit: 'Viên',
    }, {
      id: '22222222-2222-4222-8222-222222222222', code: 'MED-IBU', name: 'Ibuprofen 200mg', active: true,
    }]);
  }

  function goToMedicationStep(): void {
    (fixture.nativeElement.querySelector('[data-testid="template-step-2"]') as HTMLButtonElement).click();
    fixture.detectChanges();
    (fixture.nativeElement.querySelector('[data-testid="template-step-next"]') as HTMLButtonElement).click();
    fixture.detectChanges();
  }

  function setValue(testId: string, value: string): void {
    const control = fixture.nativeElement.querySelector(`[data-testid="${testId}"]`) as HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement;
    control.value = value;
    control.dispatchEvent(new Event(control instanceof HTMLSelectElement ? 'change' : 'input'));
  }
});

function template() {
  return {
    id: 'template-1', code: 'NOI-TQ', name: 'Khám nội tổng quát', specialty: 'Nội tổng quát',
    clinicServiceId: 'service-1', description: 'Mẫu thường dùng',
    fieldDefinition: JSON.stringify({ examinationNotes: 'Khám nội tổng quát.' }),
    active: true, createdBy: 'admin', updatedAt: '2026-08-14T08:00:00Z',
  };
}
