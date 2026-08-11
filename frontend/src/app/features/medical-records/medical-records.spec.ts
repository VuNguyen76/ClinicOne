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
    expectHistoryRequest(0).flush(history([record()]));
    http.expectOne('/api/v1/patient-profiles').flush([profile()]);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Đau đầu do căng thẳng');
    expect(fixture.nativeElement.textContent).toContain('Đã ký');
    expect(fixture.nativeElement.querySelector('a[href="/medical-records/record-1"]')).not.toBeNull();
  });

  it('shows an empty state when the patient has no signed records', () => {
    expectHistoryRequest(0).flush(history([]));
    http.expectOne('/api/v1/patient-profiles').flush([profile()]);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Chưa có phiếu khám đã ký');
    expect(fixture.nativeElement.textContent).not.toContain('Phiếu khám tổng quát');
  });

  it('applies profile and date filters from the patient history screen', () => {
    expectHistoryRequest(0).flush(history([record()]));
    http.expectOne('/api/v1/patient-profiles').flush([profile()]);
    fixture.detectChanges();

    const profileSelect = fixture.nativeElement.querySelector('[data-testid="history-profile"]') as HTMLSelectElement;
    profileSelect.value = 'profile-1';
    profileSelect.dispatchEvent(new Event('change'));
    const from = fixture.nativeElement.querySelector('[data-testid="history-from"]') as HTMLInputElement;
    from.value = '2026-08-01';
    from.dispatchEvent(new Event('input'));
    const to = fixture.nativeElement.querySelector('[data-testid="history-to"]') as HTMLInputElement;
    to.value = '2026-08-20';
    to.dispatchEvent(new Event('input'));
    (fixture.nativeElement.querySelector('[data-testid="apply-history-filter"]') as HTMLButtonElement).click();

    const request = http.expectOne((candidate) => candidate.url === '/api/v1/medical-records'
      && candidate.params.get('profileId') === 'profile-1'
      && candidate.params.get('from') === '2026-08-01'
      && candidate.params.get('to') === '2026-08-20');
    request.flush(history([record()]));
  });

  it('loads only the next history page when the patient asks for more records', () => {
    expectHistoryRequest(0).flush(history([record()], 0, 21, 2));
    http.expectOne('/api/v1/patient-profiles').flush([profile()]);
    fixture.detectChanges();

    (fixture.nativeElement.querySelector('[data-testid="next-history-page"]') as HTMLButtonElement).click();
    expectHistoryRequest(1).flush(history([record()], 1, 21, 2));
  });

  function expectHistoryRequest(page: number) {
    return http.expectOne((candidate) => candidate.url === '/api/v1/medical-records'
      && candidate.params.get('page') === String(page) && candidate.params.get('size') === '20');
  }
});

function record() {
  return {
    id: 'record-1', examinationId: 'exam-1', appointmentCode: 'CL-20260807-0009', doctorName: 'Bác sĩ Nguyễn An',
    reason: 'Đau đầu', examinationNotes: 'Mạch ổn', diagnosis: 'Đau đầu do căng thẳng', conclusion: 'Theo dõi thêm',
    treatmentPlan: 'Nghỉ ngơi', prescription: null, followUpDate: null, signedAt: '2026-08-07T09:30:00Z',
  };
}

function history(items: ReturnType<typeof record>[], page = 0, totalElements = items.length, totalPages = items.length ? 1 : 0) {
  return { items, page, size: 20, totalElements, totalPages };
}

function profile() {
  return { id: 'profile-1', fullName: 'Nguyễn Thanh Vũ', dateOfBirth: '2005-06-07', gender: 'Nam', primary: true };
}
