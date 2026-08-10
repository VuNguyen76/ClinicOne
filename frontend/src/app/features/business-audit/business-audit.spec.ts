import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { BusinessAudit } from './business-audit';

describe('BusinessAudit', () => {
  let fixture: ComponentFixture<BusinessAudit>;
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [BusinessAudit],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();
    fixture = TestBed.createComponent(BusinessAudit);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  it('requires an entity id before querying', () => {
    fixture.componentInstance['search']();
    expect(fixture.componentInstance['error']()).toContain('mã đối tượng');
  });

  it('loads a paged history and exposes navigation state', () => {
    fixture.componentInstance['entityId'].set('entity-1');
    fixture.componentInstance['search']();
    const request = http.expectOne((item) => item.url === '/api/v1/admin/audit/search');
    expect(request.request.params.get('size')).toBe('50');
    request.flush({ items: [{ id: 'log-1', eventId: 'event-1', entityType: 'APPOINTMENT', entityId: 'entity-1', previousStatus: 'BOOKED', nextStatus: 'CHECKED_IN', eventType: 'CHECK_IN', actor: 'staff', reason: null, occurredAt: '2026-08-10T01:00:00Z' }], page: 0, size: 50, totalElements: 1, totalPages: 1, last: true });
    expect(fixture.componentInstance['items']().length).toBe(1);
    expect(fixture.componentInstance['totalElements']()).toBe(1);
  });
});
