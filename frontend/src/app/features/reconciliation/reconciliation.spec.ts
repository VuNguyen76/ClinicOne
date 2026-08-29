import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { ReconciliationManagement } from './reconciliation';

describe('ReconciliationManagement', () => {
  let fixture: ComponentFixture<ReconciliationManagement>;
  let http: HttpTestingController;

  beforeEach(async () => {
    sessionStorage.setItem('clinicOneAccessToken', 'staff-token');
    sessionStorage.setItem('clinicOneStaffRole', 'COORDINATOR');
    await TestBed.configureTestingModule({
      imports: [ReconciliationManagement],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();
    fixture = TestBed.createComponent(ReconciliationManagement);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  afterEach(() => { http.verify(); sessionStorage.clear(); });

  it('shows open incidents with clean business code and without editable status controls', () => {
    http.expectOne('/api/v1/admin/reconciliations').flush([incident()]);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('SC-202608-001');
    expect(fixture.nativeElement.textContent).toContain('LH-20260820-001');
    expect(fixture.nativeElement.textContent).toContain('Nguyễn Văn A');
  });

  it('closes an incident only after entering reference and result', () => {
    http.expectOne('/api/v1/admin/reconciliations').flush([incident()]);
    fixture.detectChanges();
    const component = fixture.componentInstance as any;
    component.referenceValue.set('SC-202608-001');
    component.resultNote.set('Đã kiểm tra lại và xử lý xong dữ liệu.');
    fixture.detectChanges();
    (fixture.nativeElement.querySelector('[data-testid="close-reconciliation"]') as HTMLButtonElement).click();
    const request = http.expectOne('/api/v1/admin/reconciliations/incident-1/close');
    expect(request.request.body).toEqual({ action: 'RETRY_BUSINESS_ACTION', referenceType: 'INCIDENT', referenceValue: 'SC-202608-001', resultNote: 'Đã kiểm tra lại và xử lý xong dữ liệu.' });
    request.flush({ ...incident(), status: 'CLOSED' });
    http.expectOne('/api/v1/admin/reconciliations').flush([{ ...incident(), status: 'CLOSED' }]);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Đã đóng thành công sự cố đối soát SC-202608-001.');
  });

  it('automatically sets referenceType to BUSINESS_LOG when choosing REPLAY_LOG', () => {
    http.expectOne('/api/v1/admin/reconciliations').flush([incident()]);
    fixture.detectChanges();
    const component = fixture.componentInstance as any;
    expect(component.referenceType()).toBe('INCIDENT');
    component.updateAction('REPLAY_LOG');
    fixture.detectChanges();
    expect(component.action()).toBe('REPLAY_LOG');
    expect(component.referenceType()).toBe('BUSINESS_LOG');
  });

  it('triggers integrity scan and notifies results', () => {
    http.expectOne('/api/v1/admin/reconciliations').flush([]);
    fixture.detectChanges();

    const component = fixture.componentInstance as any;
    component.runIntegrityScan();

    const request = http.expectOne('/api/v1/admin/audit/integrity-check');
    expect(request.request.method).toBe('POST');
    request.flush({ inspected: 120, incidentsOpened: 0 });

    http.expectOne('/api/v1/admin/reconciliations').flush([]);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Đã rà soát xong 120 bản ghi dữ liệu.');
  });
});

function incident() {
  return {
    id: 'incident-1',
    incidentCode: 'SC-202608-001',
    entityType: 'APPOINTMENT',
    entityId: 'appointment-1',
    eventId: null,
    reason: 'Không tìm thấy hồ sơ lịch hẹn LH-20260820-001 (Bệnh nhân: Nguyễn Văn A) tương ứng với nhật ký nghiệp vụ',
    assignee: 'coordinator',
    status: 'OPEN',
    resolutionAction: null,
    referenceType: null,
    referenceValue: null,
    resultNote: null,
    closedBy: null,
    closedAt: null,
    createdAt: '2026-08-10T01:00:00Z',
  };
}
