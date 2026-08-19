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

  it('shows open incidents without editable status controls', () => {
    http.expectOne('/api/v1/admin/reconciliations?status=OPEN').flush([incident()]);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('INC-TEST');
    expect(fixture.nativeElement.querySelector('[data-testid="status-input"]')).toBeNull();
  });

  it('closes an incident only after entering reference and result', () => {
    http.expectOne('/api/v1/admin/reconciliations?status=OPEN').flush([incident()]);
    fixture.detectChanges();
    const component = fixture.componentInstance as any;
    component.referenceValue.set('INC-TEST');
    component.resultNote.set('Đã kiểm tra lại và xử lý xong dữ liệu.');
    fixture.detectChanges();
    (fixture.nativeElement.querySelector('[data-testid="close-reconciliation"]') as HTMLButtonElement).click();
    const request = http.expectOne('/api/v1/admin/reconciliations/incident-1/close');
    expect(request.request.body).toEqual({ action: 'RETRY_BUSINESS_ACTION', referenceType: 'INCIDENT', referenceValue: 'INC-TEST', resultNote: 'Đã kiểm tra lại và xử lý xong dữ liệu.' });
    request.flush({ ...incident(), status: 'CLOSED' });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Đã đóng đối soát INC-TEST.');
  });

  it('does not show closing controls to an administrator', () => {
    http.expectOne('/api/v1/admin/reconciliations?status=OPEN').flush([incident()]);
    const component = fixture.componentInstance as any;
    component.canClose = () => false;
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[data-testid="close-reconciliation"]')).toBeNull();
    expect(fixture.nativeElement.textContent).toContain('chỉ được xem');
  });

  it('automatically sets referenceType to BUSINESS_LOG when choosing REPLAY_LOG', () => {
    http.expectOne('/api/v1/admin/reconciliations?status=OPEN').flush([incident()]);
    fixture.detectChanges();
    const component = fixture.componentInstance as any;
    expect(component.referenceType()).toBe('INCIDENT');
    component.updateAction('REPLAY_LOG');
    fixture.detectChanges();
    expect(component.action()).toBe('REPLAY_LOG');
    expect(component.referenceType()).toBe('BUSINESS_LOG');
  });
});

function incident() {
  return { id: 'incident-1', incidentCode: 'INC-TEST', entityType: 'APPOINTMENT', entityId: 'appointment-1', eventId: null, reason: 'Thiếu nhật ký nghiệp vụ', assignee: 'coordinator', status: 'OPEN', resolutionAction: null, referenceType: null, referenceValue: null, resultNote: null, closedBy: null, closedAt: null, createdAt: '2026-08-10T01:00:00Z' };
}
