import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { DoctorMedicalRecords } from './doctor-medical-records';

describe('DoctorMedicalRecords', () => {
  let fixture: ComponentFixture<DoctorMedicalRecords>;
  let http: HttpTestingController;

  beforeEach(async () => {
    sessionStorage.setItem('clinicOneStaffRole', 'DOCTOR');
    sessionStorage.setItem('clinicOneStaffRoles', JSON.stringify(['DOCTOR']));
    await TestBed.configureTestingModule({
      imports: [DoctorMedicalRecords],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();
    fixture = TestBed.createComponent(DoctorMedicalRecords);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  afterEach(() => {
    fixture.destroy();
    http.verify();
    sessionStorage.clear();
  });

  it('loads signed patient cases and opens the prescription from its popup', () => {
    http.expectOne((request) => request.url === '/api/v1/doctor/medical-records'
      && request.params.get('page') === '0'
      && request.params.get('size') === '20').flush({
        items: [summaryRecord()], page: 0, size: 20, totalElements: 1, totalPages: 1,
      });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="doctor-record-row"]')?.textContent)
      .toContain('Nguyễn Thị Mai');
    expect(fixture.nativeElement.textContent).toContain('Tăng huyết áp');

    (fixture.nativeElement.querySelector('[data-testid="doctor-record-row"]') as HTMLButtonElement).click();
    fixture.detectChanges();
    http.expectOne('/api/v1/doctor/medical-records/record-1').flush(detailRecord());
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="record-diagnosis"]')?.textContent)
      .toContain('Tăng huyết áp');
    expect((fixture.nativeElement.querySelector('[data-testid="prescription-dialog"]') as HTMLDialogElement).open).toBe(false);
    (fixture.nativeElement.querySelector('[data-testid="prescription-open"]') as HTMLButtonElement).click();
    fixture.detectChanges();

    expect((fixture.nativeElement.querySelector('[data-testid="prescription-dialog"]') as HTMLDialogElement).open).toBe(true);
    expect(fixture.nativeElement.querySelector('[data-testid="prescription-line"]')?.textContent)
      .toContain('Amlodipine');

    (fixture.nativeElement.querySelector('[data-testid="prescription-close"]') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect((fixture.nativeElement.querySelector('[data-testid="prescription-dialog"]') as HTMLDialogElement).open).toBe(false);
  });

  it('submits search and date filters to the doctor-scoped history API', () => {
    http.expectOne((request) => request.url === '/api/v1/doctor/medical-records').flush({
      items: [], page: 0, size: 20, totalElements: 0, totalPages: 0,
    });
    fixture.detectChanges();

    const search = fixture.nativeElement.querySelector('[data-testid="record-search"]') as HTMLInputElement;
    search.value = 'Nguyễn Thị Mai';
    search.dispatchEvent(new Event('input'));
    const from = fixture.nativeElement.querySelector('[data-testid="record-from"]') as HTMLInputElement;
    from.value = '2026-10-01';
    from.dispatchEvent(new Event('input'));
    const to = fixture.nativeElement.querySelector('[data-testid="record-to"]') as HTMLInputElement;
    to.value = '2026-10-13';
    to.dispatchEvent(new Event('input'));
    (fixture.nativeElement.querySelector('[data-testid="record-search-submit"]') as HTMLButtonElement).click();

    const request = http.expectOne((candidate) => candidate.url === '/api/v1/doctor/medical-records'
      && candidate.params.get('search') === 'Nguyễn Thị Mai'
      && candidate.params.get('from') === '2026-10-01'
      && candidate.params.get('to') === '2026-10-13');
    request.flush({ items: [], page: 0, size: 20, totalElements: 0, totalPages: 0 });
  });
});

function summaryRecord() {
  return {
    id: 'record-1', appointmentCode: 'CL-20261012-0001', patientName: 'Nguyễn Thị Mai',
    specialty: 'Nội tổng quát', appointmentDate: '2026-10-12', reason: 'Đau đầu',
    examinationNotes: null, diagnosis: 'Tăng huyết áp', conclusion: null, treatmentPlan: null,
    prescription: null, prescriptionLines: [], followUpDate: null, followUpDays: null,
    followUpNote: null, signedAt: '2026-10-12T03:00:00Z', bloodPressure: null,
    heartRate: null, temperature: null, spO2: null, weight: null, height: null, allergySummary: null,
  };
}

function detailRecord() {
  return {
    ...summaryRecord(), examinationNotes: 'Khám tổng quát ổn định.', conclusion: 'Theo dõi huyết áp',
    treatmentPlan: 'Uống thuốc buổi sáng.', prescriptionLines: [{
      id: 'line-1', medicationId: 'med-1', medicationCode: 'AMLO5', medicationName: 'Amlodipine 5 mg',
      dosage: '1 viên', frequency: 'Mỗi sáng', durationDays: 30, quantity: 30, instructions: 'Sau ăn sáng',
    }],
  };
}
