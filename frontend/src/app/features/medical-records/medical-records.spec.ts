import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { MedicalRecords } from './medical-records';

describe('MedicalRecords', () => {
  let fixture: ComponentFixture<MedicalRecords>;
  let http: HttpTestingController;

  beforeEach(async () => {
    sessionStorage.setItem('clinicOneAccessToken', 'patient-token');
    await TestBed.configureTestingModule({
      imports: [MedicalRecords],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(MedicalRecords);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  afterEach(() => {
    http.verify();
    sessionStorage.clear();
  });

  it('shows only the signed records returned by the patient endpoint', () => {
    http.expectOne('/api/v1/medical-records').flush([record()]);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Đau đầu do căng thẳng');
    expect(fixture.nativeElement.textContent).toContain('Đã ký');
    expect(fixture.nativeElement.querySelector('a[href="/medical-records/record-1"]')).not.toBeNull();
  });

  it('shows an empty state when the patient has no signed records', () => {
    http.expectOne('/api/v1/medical-records').flush([]);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Chưa có phiếu khám đã ký');
    expect(fixture.nativeElement.textContent).not.toContain('Phiếu khám tổng quát');
  });
});

function record() {
  return {
    id: 'record-1', examinationId: 'exam-1', appointmentCode: 'CL-20260807-0009', doctorName: 'Bác sĩ Nguyễn An',
    reason: 'Đau đầu', examinationNotes: 'Mạch ổn', diagnosis: 'Đau đầu do căng thẳng', conclusion: 'Theo dõi thêm',
    treatmentPlan: 'Nghỉ ngơi', prescription: null, followUpDate: null, signedAt: '2026-08-07T09:30:00Z',
  };
}
