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
        provideRouter([]),
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
    http.expectOne('/api/v1/doctor/examinations/ticket-1').flush(examination());
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="patient-summary"]').textContent)
      .toContain('Nguyễn Thanh Vũ');
    expect(fixture.nativeElement.querySelector('[data-testid="medical-form"]')).toBeTruthy();
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

  it('autosaves edited clinical fields after the doctor pauses typing', () => {
    vi.useFakeTimers();
    http.expectOne('/api/v1/doctor/examinations/ticket-1').flush(examination());
    fixture.detectChanges();

    const reason = fixture.nativeElement.querySelector('textarea[formControlName="reason"]') as HTMLTextAreaElement;
    reason.value = 'Đau đầu kéo dài';
    reason.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    vi.advanceTimersByTime(1200);
    const request = http.expectOne('/api/v1/doctor/examinations/ticket-1/draft');
    expect(request.request.method).toBe('PUT');
    expect(request.request.body.reason).toBe('Đau đầu kéo dài');
    expect(request.request.body.recordVersion).toBe(0);
    request.flush({ ...examination(), reason: 'Đau đầu kéo dài' });
    vi.useRealTimers();
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
    fixture.detectChanges();
    (fixture.nativeElement.querySelector('[data-testid="sign-record"]') as HTMLButtonElement).click();

    const request = http.expectOne('/api/v1/doctor/examinations/ticket-1/sign');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toMatchObject(fields);
    expect(request.request.body.recordVersion).toBe(0);
    request.flush({ ...examination(), ...fields, status: 'COMPLETED', signedAt: '2026-08-07T09:30:00Z' });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Đã ký phiếu khám');
    expect((fixture.nativeElement.querySelector('textarea[formControlName="reason"]') as HTMLTextAreaElement).disabled).toBe(true);
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
    followUpDate: null,
    status: 'IN_PROGRESS',
    signedAt: null,
    recordVersion: 0,
  };
}
