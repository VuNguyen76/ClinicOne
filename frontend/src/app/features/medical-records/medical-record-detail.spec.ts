import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { ActivatedRoute } from '@angular/router';
import { MedicalRecordDetail } from './medical-record-detail';

describe('MedicalRecordDetail', () => {
  let fixture: ComponentFixture<MedicalRecordDetail>;
  let http: HttpTestingController;

  beforeEach(async () => {
    sessionStorage.setItem('clinicOneAccessToken', 'patient-token');
    await TestBed.configureTestingModule({
      imports: [MedicalRecordDetail],
      providers: [
        provideHttpClient(), provideHttpClientTesting(), provideRouter([]),
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: { get: () => 'record-1' } } } },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(MedicalRecordDetail);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  afterEach(() => {
    http.verify();
    sessionStorage.clear();
  });

  it('renders the signed result and prescription when present', () => {
    http.expectOne('/api/v1/medical-records/record-1').flush({
      id: 'record-1', examinationId: 'exam-1', appointmentCode: 'CL-20260807-0009', doctorName: 'Bác sĩ Nguyễn An',
      reason: 'Đau đầu', examinationNotes: 'Mạch ổn', diagnosis: 'Đau đầu do căng thẳng', conclusion: 'Theo dõi thêm',
      treatmentPlan: 'Nghỉ ngơi', prescription: 'Paracetamol 500mg', followUpDate: '2026-08-20',
      signedAt: '2026-08-07T09:30:00Z',
    });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Phiếu khám đã ký');
    expect(fixture.nativeElement.textContent).toContain('Paracetamol 500mg');
    expect(fixture.nativeElement.textContent).toContain('20/8/2026');
  });

  it('renders each signed prescription line without exposing an editable form', () => {
    http.expectOne('/api/v1/medical-records/record-1').flush({
      id: 'record-1', examinationId: 'exam-1', appointmentCode: 'CL-20260807-0009', doctorName: 'Bác sĩ Nguyễn An',
      reason: 'Đau đầu', examinationNotes: 'Mạch ổn', diagnosis: 'Đau đầu do căng thẳng', conclusion: 'Theo dõi thêm',
      treatmentPlan: 'Nghỉ ngơi', prescription: null, prescriptionLines: [{
        medicationId: 'medicine-1', medicationName: 'Paracetamol', dosage: '500 mg', quantity: 10, instructions: 'Uống sau ăn',
      }], followUpDate: null, signedAt: '2026-08-07T09:30:00Z',
    });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Paracetamol');
    expect(fixture.nativeElement.textContent).toContain('10 viên');
    expect(fixture.nativeElement.querySelector('input, textarea, button[data-testid="edit-prescription"]')).toBeFalsy();
  });

  it('shows the structured follow-up plan on a signed record', () => {
    http.expectOne('/api/v1/medical-records/record-1').flush({
      id: 'record-1', examinationId: 'exam-1', appointmentCode: 'CL-20260807-0009', doctorName: 'Bác sĩ Nguyễn An',
      reason: 'Đau đầu', examinationNotes: 'Mạch ổn', diagnosis: 'Đau đầu căng thẳng', conclusion: 'Theo dõi thêm',
      treatmentPlan: 'Nghỉ ngơi', prescription: null, followUpDate: null, followUpDays: 14,
      followUpNote: 'Tái khám nếu triệu chứng còn kéo dài', signedAt: '2026-08-07T09:30:00Z',
    });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Sau 14 ngày');
    expect(fixture.nativeElement.textContent).toContain('Tái khám nếu triệu chứng còn kéo dài');
  });

  it('does not expose a draft when the API denies the record', () => {
    http.expectOne('/api/v1/medical-records/record-1').flush({ message: 'Không tìm thấy phiếu khám đã ký.' }, {
      status: 404, statusText: 'Not Found',
    });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Không tìm thấy phiếu khám đã ký');
    expect(fixture.nativeElement.textContent).not.toContain('Phiếu khám đã ký');
  });
});
