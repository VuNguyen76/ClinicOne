import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { AdminStatistics } from './admin-statistics';

describe('AdminStatistics', () => {
  let fixture: ComponentFixture<AdminStatistics>;
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AdminStatistics],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(AdminStatistics);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  it('loads configured specialties and uses the selected specialty for the report', () => {
    http.expectOne('/api/v1/admin/doctors').flush([]);
    http.expectOne('/api/v1/specialties').flush([
      { code: 'NOI', name: 'Nội tổng quát', description: '' },
      { code: 'TIM', name: 'Tim mạch', description: '' },
    ]);

    const reportRequest = http.expectOne((request) => request.url === '/api/v1/admin/statistics');
    expect(reportRequest.request.params.get('specialty')).toBe('Nội tổng quát');
    reportRequest.flush(report());
    fixture.detectChanges();

    const specialty = fixture.nativeElement.querySelector('select') as HTMLSelectElement;
    expect(Array.from(specialty.options).map((option) => option.textContent?.trim())).toEqual([
      'Chọn chuyên khoa', 'Nội tổng quát', 'Tim mạch',
    ]);
    expect(fixture.nativeElement.textContent).toContain('Tổng lịch hẹn');
  });

  it('does not send a report request before the specialty catalog is available', () => {
    http.expectOne('/api/v1/admin/doctors').flush([]);
    expect(() => http.expectOne('/api/v1/admin/statistics')).toThrow();
    http.expectOne('/api/v1/specialties').flush([{ code: 'NOI', name: 'Nội tổng quát', description: '' }]);
    const reportRequest = http.expectOne((request) => request.url === '/api/v1/admin/statistics');
    reportRequest.flush(report());
  });
});

function report() {
  return {
    from: '2026-08-10', to: '2026-08-10', specialty: 'Nội tổng quát', doctorId: null,
    totalAppointments: 0, checkedInAppointments: 0, absentAppointments: 0, cancelledAppointments: 0,
    completedAppointments: 0, notPerformedAppointments: 0, averageWaitMinutes: null,
    averageExaminationMinutes: null, groupBy: 'DAY', buckets: [],
  };
}
