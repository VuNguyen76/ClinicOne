import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { ActivatedRoute } from '@angular/router';
import { DoctorExamination } from './doctor-examination';

describe('DoctorExamination', () => {
  let fixture: ComponentFixture<DoctorExamination>;
  let http: HttpTestingController;

  beforeEach(async () => {
    sessionStorage.setItem('clinicOneAccessToken', 'doctor-token');
    sessionStorage.setItem('clinicOneStaffRole', 'DOCTOR');
    await TestBed.configureTestingModule({
      imports: [DoctorExamination],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([{ path: 'doctor', component: DoctorExamination }]),
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: { get: () => 'ticket-1' } } } },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(DoctorExamination);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  afterEach(() => {
    http.verify();
    sessionStorage.clear();
  });

  it('loads the active examination and renders the patient summary', () => {
    http.expectOne('/api/v1/doctor/examinations/ticket-1').flush({
      ...examination(), draftSavedAt: '2026-08-11T09:30:00Z',
    });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="patient-summary"]').textContent)
      .toContain('Nguyễn Thanh Vũ');
    expect(fixture.nativeElement.querySelector('[data-testid="medical-form"]')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('[data-testid="draft-saved-at"]')).toBeTruthy();
  });

  it('shows the clinical content of a signed previous examination', () => {
    const previousRecord = {
      id: 'record-old', examinationId: 'exam-old', appointmentCode: 'CLN-OLD',
      doctorName: 'BS. Trần Bình', reason: 'Đau đầu', examinationNotes: 'Đau hai ngày',
      diagnosis: 'Đau đầu căng thẳng', conclusion: 'Theo dõi tại nhà',
      treatmentPlan: 'Nghỉ ngơi và uống đủ nước', prescription: null,
      followUpDate: null, signedAt: '2026-07-01T09:00:00Z',
    };
    http.expectOne('/api/v1/doctor/examinations/ticket-1')
      .flush({ ...examination(), history: [previousRecord] });
    fixture.detectChanges();

    const history = fixture.nativeElement.querySelector('[data-testid="medical-history-record-old"]');
    expect(history?.textContent).toContain('Đau đầu căng thẳng');
    expect(history?.textContent).toContain('Theo dõi tại nhà');
    expect(history?.textContent).toContain('Nghỉ ngơi và uống đủ nước');
  });

  it('loads active templates for the current specialty only when the doctor opens the template panel', () => {
    http.expectOne('/api/v1/doctor/examinations/ticket-1').flush(examination());
    fixture.detectChanges();

    (fixture.nativeElement.querySelector('[data-testid="open-medical-templates"]') as HTMLButtonElement).click();
    const request = http.expectOne((candidate) => candidate.url === '/api/v1/medical-record-templates'
      && candidate.params.get('specialty') === 'Nội tổng quát'
      && candidate.params.get('clinicServiceId') === 'service-1'
      && candidate.params.get('activeOnly') === 'true');
    expect(request.request.method).toBe('GET');
    request.flush([medicalTemplate()]);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="medical-template-option-0"]')?.textContent)
      .toContain('Khám nội tổng quát');
  });

  it('previews and applies template content to an empty draft', () => {
    http.expectOne('/api/v1/doctor/examinations/ticket-1').flush(examination());
    fixture.detectChanges();
    (fixture.nativeElement.querySelector('[data-testid="open-medical-templates"]') as HTMLButtonElement).click();
    http.expectOne((candidate) => candidate.url === '/api/v1/medical-record-templates').flush([medicalTemplate()]);
    fixture.detectChanges();

    (fixture.nativeElement.querySelector('[data-testid="medical-template-option-0"]') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[data-testid="medical-template-preview"]')?.textContent)
      .toContain('Khám theo trình tự nội tổng quát.');

    (fixture.nativeElement.querySelector('[data-testid="apply-medical-template"]') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect((fixture.nativeElement.querySelector('textarea[formControlName="examinationNotes"]') as HTMLTextAreaElement).value)
      .toBe('Khám theo trình tự nội tổng quát.');
    expect((fixture.nativeElement.querySelector('textarea[formControlName="treatmentPlan"]') as HTMLTextAreaElement).value)
      .toBe('Tư vấn chăm sóc và tái khám khi cần.');
    expect(fixture.nativeElement.querySelector('[data-testid="confirm-template-overwrite"]')).toBeNull();
  });

  it('does not overwrite entered content until the doctor explicitly confirms', () => {
    http.expectOne('/api/v1/doctor/examinations/ticket-1').flush(examination());
    fixture.detectChanges();
    const notes = fixture.nativeElement.querySelector('textarea[formControlName="examinationNotes"]') as HTMLTextAreaElement;
    notes.value = 'Nội dung bác sĩ đang nhập';
    notes.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    (fixture.nativeElement.querySelector('[data-testid="open-medical-templates"]') as HTMLButtonElement).click();
    http.expectOne((candidate) => candidate.url === '/api/v1/medical-record-templates').flush([medicalTemplate()]);
    fixture.detectChanges();
    (fixture.nativeElement.querySelector('[data-testid="medical-template-option-0"]') as HTMLButtonElement).click();
    fixture.detectChanges();
    (fixture.nativeElement.querySelector('[data-testid="apply-medical-template"]') as HTMLButtonElement).click();
    fixture.detectChanges();

    expect(notes.value).toBe('Nội dung bác sĩ đang nhập');
    expect(fixture.nativeElement.querySelector('[data-testid="confirm-template-overwrite"]')).toBeTruthy();
    (fixture.nativeElement.querySelector('[data-testid="confirm-template-overwrite"]') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(notes.value).toBe('Khám theo trình tự nội tổng quát.');
  });

  it('saves a draft without leaving the workspace', () => {
    http.expectOne('/api/v1/doctor/examinations/ticket-1').flush(examination());
    fixture.detectChanges();
    const reason = fixture.nativeElement.querySelector('textarea[formControlName="reason"]') as HTMLTextAreaElement;
    reason.value = 'Đau đầu';
    reason.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    (fixture.nativeElement.querySelector('[data-testid="save-draft"]') as HTMLButtonElement).click();
    const request = http.expectOne('/api/v1/doctor/examinations/ticket-1/draft');
    expect(request.request.method).toBe('PUT');
    expect(request.request.body.reason).toBe('Đau đầu');
    expect(request.request.body.recordVersion).toBe(0);
    request.flush({ ...examination(), reason: 'Đau đầu' });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Đã lưu bản nháp');
  });

  it('binds, computes BMI dynamically, and saves clinical vitals in draft', () => {
    http.expectOne('/api/v1/doctor/examinations/ticket-1').flush(examination());
    fixture.detectChanges();

    const bp = fixture.nativeElement.querySelector('[data-testid="vital-blood-pressure"]') as HTMLInputElement;
    const hr = fixture.nativeElement.querySelector('[data-testid="vital-heart-rate"]') as HTMLInputElement;
    const temp = fixture.nativeElement.querySelector('[data-testid="vital-temperature"]') as HTMLInputElement;
    const spo2 = fixture.nativeElement.querySelector('[data-testid="vital-spo2"]') as HTMLInputElement;
    const weight = fixture.nativeElement.querySelector('[data-testid="vital-weight"]') as HTMLInputElement;
    const height = fixture.nativeElement.querySelector('[data-testid="vital-height"]') as HTMLInputElement;
    const allergy = fixture.nativeElement.querySelector('[data-testid="vital-allergy"]') as HTMLInputElement;

    bp.value = '120/80';
    bp.dispatchEvent(new Event('input'));
    hr.value = '75';
    hr.dispatchEvent(new Event('input'));
    temp.value = '36.8';
    temp.dispatchEvent(new Event('input'));
    spo2.value = '98';
    spo2.dispatchEvent(new Event('input'));
    weight.value = '65';
    weight.dispatchEvent(new Event('input'));
    height.value = '170';
    height.dispatchEvent(new Event('input'));
    allergy.value = 'Dị ứng phấn hoa';
    allergy.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    expect(fixture.componentInstance['bmi']()).toBe(22.5);
    expect(fixture.nativeElement.textContent).toContain('22.5 kg/m²');
    expect(fixture.nativeElement.textContent).toContain('Bình thường');

    (fixture.nativeElement.querySelector('[data-testid="save-draft"]') as HTMLButtonElement).click();
    const request = http.expectOne('/api/v1/doctor/examinations/ticket-1/draft');
    expect(request.request.body.bloodPressure).toBe('120/80');
    expect(request.request.body.heartRate).toBe(75);
    expect(request.request.body.temperature).toBe(36.8);
    expect(request.request.body.spO2).toBe(98);
    expect(request.request.body.weight).toBe(65);
    expect(request.request.body.height).toBe(170);
    expect(request.request.body.allergySummary).toBe('Dị ứng phấn hoa');
    request.flush({ ...examination(), bloodPressure: '120/80', weight: 65, height: 170 });
  });

  it('saves each prescribed medicine as a complete line', () => {
    http.expectOne('/api/v1/doctor/examinations/ticket-1').flush(examination());
    fixture.detectChanges();

    (fixture.nativeElement.querySelector('[data-testid="add-prescription-line"]') as HTMLButtonElement).click();
    fixture.detectChanges();
    const setValue = (name: string, value: string) => {
      const input = fixture.nativeElement.querySelector(`[data-testid="prescription-${name}-0"]`) as HTMLInputElement;
      input.value = value;
      input.dispatchEvent(new Event('input'));
    };
    setValue('name', 'Paracetamol 500 mg');
    setValue('dosage', '500 mg');
    setValue('quantity', '10');
    setValue('instructions', 'Uống sau ăn');
    fixture.detectChanges();
    http.expectOne('/api/v1/doctor/medications/suggestions?query=Paracetamol%20500%20mg').flush([]);

    (fixture.nativeElement.querySelector('[data-testid="save-draft"]') as HTMLButtonElement).click();
    const request = http.expectOne('/api/v1/doctor/examinations/ticket-1/draft');
    expect(request.request.body.prescription).toBe('');
    expect(request.request.body.prescriptionLines).toEqual([{
      medicationName: 'Paracetamol 500 mg', dosage: '500 mg', quantity: 10, unit: 'Viên', instructions: 'Uống sau ăn',
    }]);
    request.flush({ ...examination(), prescriptionLines: [{
      medicationName: 'Paracetamol 500 mg', dosage: '500 mg', quantity: 10, unit: 'Viên', instructions: 'Uống sau ăn',
    }] });
  });

  it('only sends a follow-up interval and note after the doctor enables it', () => {
    http.expectOne('/api/v1/doctor/examinations/ticket-1').flush(examination());
    fixture.detectChanges();

    (fixture.nativeElement.querySelector('[data-testid="toggle-follow-up"]') as HTMLButtonElement).click();
    fixture.detectChanges();
    const days = fixture.nativeElement.querySelector('[data-testid="follow-up-days"]') as HTMLInputElement;
    const note = fixture.nativeElement.querySelector('[data-testid="follow-up-note"]') as HTMLInputElement;
    days.value = '14'; days.dispatchEvent(new Event('input'));
    note.value = 'Tái khám nếu triệu chứng còn kéo dài'; note.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    (fixture.nativeElement.querySelector('[data-testid="save-draft"]') as HTMLButtonElement).click();
    const request = http.expectOne('/api/v1/doctor/examinations/ticket-1/draft');
    expect(request.request.body.followUpDays).toBe(14);
    expect(request.request.body.followUpNote).toBe('Tái khám nếu triệu chứng còn kéo dài');
  });

  it('autosaves edited clinical fields after ten seconds when the draft changed', () => {
    vi.useFakeTimers();
    http.expectOne('/api/v1/doctor/examinations/ticket-1').flush(examination());
    fixture.detectChanges();

    const reason = fixture.nativeElement.querySelector('textarea[formControlName="reason"]') as HTMLTextAreaElement;
    reason.value = 'Đau đầu kéo dài';
    reason.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    vi.advanceTimersByTime(9_999);
    http.expectNone('/api/v1/doctor/examinations/ticket-1/draft');
    vi.advanceTimersByTime(1);
    const request = http.expectOne('/api/v1/doctor/examinations/ticket-1/draft');
    expect(request.request.method).toBe('PUT');
    expect(request.request.body.reason).toBe('Đau đầu kéo dài');
    expect(request.request.body.recordVersion).toBe(0);
    request.flush({ ...examination(), reason: 'Đau đầu kéo dài' });
    vi.useRealTimers();
  });

  it('saves a changed draft when the doctor leaves a clinical field', () => {
    http.expectOne('/api/v1/doctor/examinations/ticket-1').flush(examination());
    fixture.detectChanges();

    const reason = fixture.nativeElement.querySelector('textarea[formControlName="reason"]') as HTMLTextAreaElement;
    reason.value = 'Đau đầu kéo dài';
    reason.dispatchEvent(new Event('input'));
    reason.dispatchEvent(new FocusEvent('focusout', { bubbles: true }));

    const request = http.expectOne('/api/v1/doctor/examinations/ticket-1/draft');
    expect(request.request.body.reason).toBe('Đau đầu kéo dài');
  });

  it('retries a failed automatic draft save at most three times every ten seconds', () => {
    vi.useFakeTimers();
    http.expectOne('/api/v1/doctor/examinations/ticket-1').flush(examination());
    fixture.detectChanges();

    const reason = fixture.nativeElement.querySelector('textarea[formControlName="reason"]') as HTMLTextAreaElement;
    reason.value = 'Đau đầu kéo dài';
    reason.dispatchEvent(new Event('input'));
    reason.dispatchEvent(new FocusEvent('focusout', { bubbles: true }));
    http.expectOne('/api/v1/doctor/examinations/ticket-1/draft').flush({}, { status: 503, statusText: 'Unavailable' });

    for (let attempt = 0; attempt < 3; attempt += 1) {
      vi.advanceTimersByTime(10_000);
      http.expectOne('/api/v1/doctor/examinations/ticket-1/draft').flush({}, { status: 503, statusText: 'Unavailable' });
    }
    vi.advanceTimersByTime(10_000);
    http.expectNone('/api/v1/doctor/examinations/ticket-1/draft');
    vi.useRealTimers();
  });

  it('suggests a diagnosis after two characters and keeps the selected text editable', () => {
    http.expectOne('/api/v1/doctor/examinations/ticket-1').flush(examination());
    fixture.detectChanges();

    const diagnosis = fixture.nativeElement.querySelector('textarea[formControlName="diagnosis"]') as HTMLTextAreaElement;
    diagnosis.value = 'Đau';
    diagnosis.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    http.expectOne((request) => request.url === '/api/v1/doctor/diagnoses/suggestions' && request.params.get('query') === 'Đau')
      .flush([{ id: 'diagnosis-1', code: 'HEADACHE_TENSION', name: 'Đau đầu căng thẳng', active: true }]);
    fixture.detectChanges();

    (fixture.nativeElement.querySelector('[data-testid="diagnosis-suggestion-0"]') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(diagnosis.value).toBe('Đau đầu căng thẳng');
    expect(diagnosis.disabled).toBe(false);
  });

  it('keeps the valid clinical text and names the field when its limit is exceeded', () => {
    http.expectOne('/api/v1/doctor/examinations/ticket-1').flush(examination());
    fixture.detectChanges();

    const field = fixture.nativeElement.querySelector('textarea[formControlName="reason"]') as HTMLTextAreaElement;
    field.value = 'a'.repeat(2001);
    field.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    expect(field.value).toHaveLength(2000);
    expect(fixture.nativeElement.textContent).toContain('Lý do khám tối đa 2.000 ký tự.');
  });

  it('keeps the valid prescription text and names the field when its limit is exceeded', () => {
    http.expectOne('/api/v1/doctor/examinations/ticket-1').flush(examination());
    fixture.detectChanges();
    (fixture.nativeElement.querySelector('[data-testid="add-prescription-line"]') as HTMLButtonElement).click();
    fixture.detectChanges();

    const field = fixture.nativeElement.querySelector('[data-testid="prescription-name-0"]') as HTMLInputElement;
    field.value = 'a'.repeat(201);
    field.dispatchEvent(new Event('input'));
    http.expectOne((request) => request.url === '/api/v1/doctor/medications/suggestions'
      && request.params.get('query') === 'a'.repeat(200)).flush([]);
    fixture.detectChanges();

    expect(field.value).toHaveLength(200);
    expect(fixture.nativeElement.textContent).toContain('Tên thuốc tối đa 200 ký tự.');
  });

  it('keeps the maximum valid follow-up interval and explains the limit', () => {
    http.expectOne('/api/v1/doctor/examinations/ticket-1').flush(examination());
    fixture.detectChanges();
    (fixture.nativeElement.querySelector('[data-testid="toggle-follow-up"]') as HTMLButtonElement).click();
    fixture.detectChanges();

    const days = fixture.nativeElement.querySelector('[data-testid="follow-up-days"]') as HTMLInputElement;
    days.value = '366';
    days.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    expect(days.value).toBe('365');
    expect(fixture.nativeElement.textContent).toContain('Số ngày tái khám tối đa 365.');
  });

  it('requires the four clinical fields and locks the form after signing', () => {
    http.expectOne('/api/v1/doctor/examinations/ticket-1').flush(examination());
    fixture.detectChanges();

    (fixture.nativeElement.querySelector('[data-testid="sign-record"]') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[role="alert"]').textContent)
      .toContain('Nhập đủ lý do khám');
    http.expectNone('/api/v1/doctor/examinations/ticket-1/sign');

    const fields: Record<string, string> = {
      reason: 'Đau đầu', examinationNotes: 'Mạch ổn', diagnosis: 'Đau đầu căng thẳng', conclusion: 'Theo dõi thêm',
    };
    Object.entries(fields).forEach(([name, value]) => {
      const control = fixture.nativeElement.querySelector(`textarea[formControlName="${name}"]`) as HTMLTextAreaElement;
      control.value = value;
      control.dispatchEvent(new Event('input'));
    });
    http.expectOne((request) => request.url === '/api/v1/doctor/diagnoses/suggestions'
      && request.params.get('query') === 'Đau đầu căng thẳng').flush([]);
    fixture.detectChanges();
    (fixture.nativeElement.querySelector('[data-testid="sign-record"]') as HTMLButtonElement).click();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="confirm-sign-record"]')).toBeTruthy();
    http.expectNone('/api/v1/doctor/examinations/ticket-1/sign');
    (fixture.nativeElement.querySelector('[data-testid="confirm-sign-record"]') as HTMLButtonElement).click();

    const request = http.expectOne('/api/v1/doctor/examinations/ticket-1/sign');
    expect(request.request.method).toBe('POST');
    expect(request.request.headers.get('Idempotency-Key')).toBeTruthy();
    expect(request.request.body).toMatchObject(fields);
    expect(request.request.body.recordVersion).toBe(0);
    request.flush({ ...examination(), ...fields, status: 'COMPLETED', signedAt: '2026-08-07T09:30:00Z' });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Đã ký phiếu khám');
    expect((fixture.nativeElement.querySelector('textarea[formControlName="reason"]') as HTMLTextAreaElement).disabled).toBe(true);
  });

  it('requires a reason before stopping an active examination and then locks the workspace', () => {
    http.expectOne('/api/v1/doctor/examinations/ticket-1').flush(examination());
    fixture.detectChanges();

    (fixture.nativeElement.querySelector('[data-testid="stop-examination"]') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[data-testid="confirm-stop-examination"]')).toBeTruthy();

    (fixture.nativeElement.querySelector('[data-testid="confirm-stop-examination"]') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Lý do dừng lượt khám phải từ 10 đến 500 ký tự.');
    http.expectNone('/api/v1/doctor/examinations/ticket-1/stop');

    const reason = fixture.nativeElement.querySelector('[data-testid="stop-examination-reason"]') as HTMLTextAreaElement;
    reason.value = 'Người bệnh cần rời phòng khám ngay.';
    reason.dispatchEvent(new Event('input'));
    (fixture.nativeElement.querySelector('[data-testid="confirm-stop-examination"]') as HTMLButtonElement).click();

    const request = http.expectOne('/api/v1/doctor/examinations/ticket-1/stop');
    expect(request.request.body).toEqual({ reason: 'Người bệnh cần rời phòng khám ngay.' });
    request.flush({ ...examination(), status: 'CANCELLED' });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Đã dừng lượt khám.');
    expect(fixture.nativeElement.querySelector('[data-testid="stop-examination"]')).toBeNull();
  });

  it('returns a wrongly opened profile to the waiting queue only after a reason is confirmed', () => {
    http.expectOne('/api/v1/doctor/examinations/ticket-1').flush(examination());
    fixture.detectChanges();

    (fixture.nativeElement.querySelector('[data-testid="wrong-profile"]') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[data-testid="confirm-wrong-profile"]')).toBeTruthy();

    const reason = fixture.nativeElement.querySelector('[data-testid="wrong-profile-reason"]') as HTMLTextAreaElement;
    reason.value = 'Bác sĩ phát hiện đang mở nhầm hồ sơ người bệnh.';
    reason.dispatchEvent(new Event('input'));
    (fixture.nativeElement.querySelector('[data-testid="confirm-wrong-profile"]') as HTMLButtonElement).click();

    const request = http.expectOne('/api/v1/doctor/examinations/ticket-1/wrong-profile');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ reason: 'Bác sĩ phát hiện đang mở nhầm hồ sơ người bệnh.' });
    request.flush({ ...examination(), status: 'SCHEDULED' });
  });

  it('opens specialty medication catalog, loads medications from /api/v1/doctor/medications, and prescribes with 1 click', () => {
    http.expectOne('/api/v1/doctor/examinations/ticket-1').flush(examination());
    fixture.detectChanges();

    const openBtn = fixture.nativeElement.querySelector('[data-testid="open-medication-catalog"]') as HTMLButtonElement;
    expect(openBtn).toBeTruthy();
    openBtn.click();
    fixture.detectChanges();

    const req = http.expectOne('/api/v1/doctor/medications');
    expect(req.request.method).toBe('GET');
    req.flush([
      { id: 'med-1', code: 'MED-PARA-500', name: 'Paracetamol 500mg (Hạ sốt, giảm đau nhanh)', active: true, category: 'Hạ sốt & Giảm đau', defaultDosage: '1 viên / lần', defaultInstructions: 'Uống sau ăn', unit: 'Viên' },
      { id: 'med-2', code: 'MED-AMOX-500', name: 'Amoxicillin 500mg (Kháng sinh)', active: true, category: 'Kháng sinh', defaultDosage: '1 viên / lần', defaultInstructions: 'Uống sau ăn', unit: 'Viên' },
    ]);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Paracetamol 500mg');

    const prescribeButtons = fixture.nativeElement.querySelectorAll('section[role="dialog"] button');
    const prescribePara = Array.from(prescribeButtons).find((b: any) => b.textContent?.includes('+ Kê thuốc')) as HTMLButtonElement;
    expect(prescribePara).toBeTruthy();
    prescribePara.click();
    fixture.detectChanges();

    expect(fixture.componentInstance['prescriptionLines'].length).toBe(1);
    expect(fixture.componentInstance['prescriptionLines'].at(0).value.medicationName).toContain('Paracetamol 500mg');
  });

  it('filters medications by category tabs and search query', () => {
    http.expectOne('/api/v1/doctor/examinations/ticket-1').flush(examination());
    fixture.detectChanges();

    (fixture.nativeElement.querySelector('[data-testid="open-medication-catalog"]') as HTMLButtonElement).click();
    fixture.detectChanges();

    const req = http.expectOne('/api/v1/doctor/medications');
    req.flush([
      { id: 'med-1', code: 'MED-PARA-500', name: 'Paracetamol 500mg', active: true, category: 'Hạ sốt & Giảm đau', defaultDosage: '1 viên / lần', defaultInstructions: 'Uống sau ăn', unit: 'Viên' },
      { id: 'med-2', code: 'MED-AMLO-5', name: 'Amlodipine 5mg', active: true, category: 'Tim mạch', defaultDosage: '1 viên / ngày', defaultInstructions: 'Uống sáng', unit: 'Viên' },
      { id: 'med-3', code: 'MED-AUGM-1000', name: 'Augmentin 1g', active: true, category: 'Kháng sinh', defaultDosage: '1 viên / lần', defaultInstructions: 'Uống sau ăn', unit: 'Viên' },
    ]);
    fixture.detectChanges();

    // Select category 'Kháng sinh'
    fixture.componentInstance['selectMedicationCategory']('Kháng sinh');
    fixture.detectChanges();

    const antibioticItems = fixture.componentInstance['filteredCatalogMedications']();
    expect(antibioticItems.every((item) => item.category === 'Kháng sinh')).toBe(true);

    // Search for 'Amlo'
    fixture.componentInstance['selectMedicationCategory']('Tất cả');
    fixture.componentInstance['medicationSearchQuery'].set('Amlo');
    fixture.detectChanges();

    const searchResults = fixture.componentInstance['filteredCatalogMedications']();
    expect(searchResults.length).toBe(1);
    expect(searchResults[0].name).toBe('Amlodipine 5mg');
  });

  it('auto-fills dosage, unit, quantity, and instructions when selecting medication from suggestions', () => {
    http.expectOne('/api/v1/doctor/examinations/ticket-1').flush(examination());
    fixture.detectChanges();

    (fixture.nativeElement.querySelector('[data-testid="add-prescription-line"]') as HTMLButtonElement).click();
    fixture.detectChanges();

    const suggestion = {
      id: 'med-acetyl-200',
      code: 'MED-ACETYL-200',
      name: 'Acetylcystein 200mg',
      active: true,
      category: 'Hô hấp',
      defaultDosage: '1 gói / lần x 2 lần/ngày',
      defaultInstructions: 'Pha với nước ấm uống sau ăn',
      unit: 'Gói',
    };

    fixture.componentInstance['selectMedication'](0, suggestion);
    fixture.detectChanges();

    const line = fixture.componentInstance['prescriptionLines'].at(0).value;
    expect(line.medicationId).toBe('med-acetyl-200');
    expect(line.medicationName).toBe('Acetylcystein 200mg');
    expect(line.dosage).toBe('1 gói / lần x 2 lần/ngày');
    expect(line.unit).toBe('Gói');
    expect(line.quantity).toBe(1);
    expect(line.instructions).toBe('Pha với nước ấm uống sau ăn');
  });

  it('triggers clinical safety warnings for duplicate medications and patient allergies', () => {
    http.expectOne('/api/v1/doctor/examinations/ticket-1').flush({
      ...examination(),
      allergySummary: 'Dị ứng Aspirin, Penicillin',
    });
    fixture.detectChanges();

    fixture.componentInstance['addPrescriptionLine']();
    fixture.componentInstance['addPrescriptionLine']();
    fixture.componentInstance['prescriptionLines'].at(0).patchValue({
      medicationName: 'Aspirin 81mg',
      dosage: '1 viên / ngày',
      quantity: 1,
      unit: 'Viên',
      instructions: 'Uống sau ăn',
    });
    fixture.componentInstance['prescriptionLines'].at(1).patchValue({
      medicationName: 'Aspirin 81mg',
      dosage: '1 viên / ngày',
      quantity: 1,
      unit: 'Viên',
      instructions: 'Uống sau ăn',
    });
    fixture.detectChanges();

    const allergyWarnings = fixture.componentInstance['allergyWarnings']();
    expect(allergyWarnings.length).toBe(1);
    expect(allergyWarnings[0].allergen).toBe('aspirin');

    const duplicateWarnings = fixture.componentInstance['duplicatePrescriptionWarnings']();
    expect(duplicateWarnings).toContain('Aspirin 81mg');
  });
});

function examination() {
  return {
    ticketId: 'ticket-1',
    appointmentId: 'appointment-1',
    examinationId: 'examination-1',
    queueNumber: 5,
    roomName: 'Phòng Nội tổng quát 01',
    appointmentCode: 'CLN-0001',
    specialty: 'Nội tổng quát',
    clinicServiceId: 'service-1',
    doctorName: 'BS. Nguyễn An',
    appointmentDate: '2026-08-06',
    startTime: '09:00:00',
    patientName: 'Nguyễn Thanh Vũ',
    patientDateOfBirth: '2005-06-07',
    patientGender: 'Nam',
    patientPhone: '0862764830',
    reason: '',
    examinationNotes: '',
    diagnosis: '',
    conclusion: '',
    treatmentPlan: '',
    prescription: '',
    prescriptionLines: [],
    followUpDate: null,
    status: 'IN_PROGRESS',
    signedAt: null,
    recordVersion: 0,
  };
}

function medicalTemplate() {
  return {
    id: 'template-1',
    code: 'NOI-TQ',
    name: 'Khám nội tổng quát',
    specialty: 'Nội tổng quát',
    clinicServiceId: 'service-1',
    description: 'Mẫu ghi nhận thường dùng cho khám nội tổng quát.',
    fieldDefinition: JSON.stringify({
      examinationNotes: 'Khám theo trình tự nội tổng quát.',
      treatmentPlan: 'Tư vấn chăm sóc và tái khám khi cần.',
    }),
    active: true,
    createdBy: 'coordinator',
    updatedAt: '2026-08-14T08:00:00Z',
  };
}
