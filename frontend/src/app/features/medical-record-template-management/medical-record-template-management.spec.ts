import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { MedicalRecordTemplateManagement } from './medical-record-template-management';

describe('MedicalRecordTemplateManagement', () => {
  let fixture: ComponentFixture<MedicalRecordTemplateManagement>;
  let http: HttpTestingController;

  beforeEach(async () => {
    sessionStorage.setItem('clinicOneAccessToken', 'admin-token');
    sessionStorage.setItem('clinicOneStaffRole', 'ADMIN');
    sessionStorage.setItem('clinicOneStaffRoles', JSON.stringify(['ADMIN']));
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
    fixture.detectChanges();

    (fixture.nativeElement.querySelector('[data-testid="save-medical-template"]') as HTMLButtonElement).click();
    const request = http.expectOne('/api/v1/medical-record-templates');
    expect(request.request.method).toBe('POST');
    expect(JSON.parse(request.request.body.fieldDefinition)).toEqual({
      examinationNotes: 'Khám theo trình tự nội tổng quát.',
      treatmentPlan: 'Tư vấn chăm sóc và tái khám khi cần.',
    });
    request.flush(template());
    http.expectOne((candidate) => candidate.url === '/api/v1/medical-record-templates').flush([]);
  });

  function flushReferenceData(templates = [template()]): void {
    http.expectOne((candidate) => candidate.url === '/api/v1/medical-record-templates').flush(templates);
    http.expectOne((candidate) => candidate.url === '/api/v1/specialties').flush([
      { code: 'NOI_TONG_QUAT', name: 'Nội tổng quát', description: '' },
    ]);
    http.expectOne('/api/v1/services').flush([
      { id: 'service-1', name: 'Khám nội tổng quát', specialty: 'Nội tổng quát', visitType: 'STANDARD', durationMinutes: 30, active: true, eligibleDoctors: [] },
    ]);
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
