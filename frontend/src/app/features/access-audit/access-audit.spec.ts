import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { AccessAuditManagement } from './access-audit';

describe('AccessAuditManagement', () => {
  let fixture: ComponentFixture<AccessAuditManagement>;
  let http: HttpTestingController;

  beforeEach(async () => {
    sessionStorage.setItem('clinicOneAccessToken', 'admin-token');
    sessionStorage.setItem('clinicOneStaffRole', 'ADMIN');
    await TestBed.configureTestingModule({
      imports: [AccessAuditManagement],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();
    fixture = TestBed.createComponent(AccessAuditManagement);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  afterEach(() => { http.verify(); sessionStorage.clear(); });

  it('loads read-only access events', () => {
    http.expectOne('/api/v1/admin/access-audit').flush([event()]);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('STAFF_LOGIN');
    expect(fixture.nativeElement.querySelector('[data-testid="audit-edit"]')).toBeNull();
  });

  it('sends entered filters when refreshing', () => {
    http.expectOne('/api/v1/admin/access-audit').flush([]);
    const component = fixture.componentInstance as any;
    component.actor.set('admin');
    component.outcome.set('FAILED');
    component.refresh();
    const request = http.expectOne((item) => item.url === '/api/v1/admin/access-audit');
    expect(request.request.params.get('actor')).toBe('admin');
    expect(request.request.params.get('outcome')).toBe('FAILED');
    request.flush([]);
  });
});

function event() {
  return { id: 'event-1', eventType: 'STAFF_LOGIN', actor: 'admin', outcome: 'SUCCESS', function: '/api/v1/staff/auth/login', ipAddress: '127.0.0.1', occurredAt: '2026-08-10T07:00:00Z' };
}
