import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { BusinessAudit } from './business-audit';

describe('BusinessAudit', () => {
  let fixture: ComponentFixture<BusinessAudit>;
  let http: HttpTestingController;

  const mockLogResponse = {
    items: [
      {
        id: 'log-001-uuid',
        eventId: 'event-001-uuid',
        entityType: 'APPOINTMENT',
        entityId: 'appt-001-uuid',
        previousStatus: 'BOOKED',
        nextStatus: 'CHECKED_IN',
        eventType: 'RECEPTION_CHECK_IN',
        actor: 'staff.reception',
        reason: 'Bệnh nhân đến tiếp đón đúng giờ',
        occurredAt: '2026-08-20T08:15:00Z',
        hash: 'e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855',
      },
      {
        id: 'log-002-uuid',
        eventId: 'event-002-uuid',
        entityType: 'QUEUE_TICKET',
        entityId: 'ticket-002-uuid',
        previousStatus: 'WAITING',
        nextStatus: 'CALLING',
        eventType: 'CALL_NEXT',
        actor: 'bs.an',
        reason: null,
        occurredAt: '2026-08-20T08:30:00Z',
        hash: 'ca978112ca1bbdcafac231b39a23dc4da786eff8147c4e72b9807785afee48bb',
      },
    ],
    page: 0,
    size: 50,
    totalElements: 2,
    totalPages: 1,
    last: true,
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [BusinessAudit],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();
    fixture = TestBed.createComponent(BusinessAudit);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('automatically loads recent operational logs on ngOnInit without requiring manual UUID', () => {
    fixture.detectChanges(); // triggers ngOnInit -> search()
    const request = http.expectOne((req) => req.url === '/api/v1/admin/audit/search');
    expect(request.request.params.get('size')).toBe('50');
    expect(request.request.params.has('identifier')).toBe(false);
    request.flush(mockLogResponse);
    fixture.detectChanges();

    expect(fixture.componentInstance['items']().length).toBe(2);
    expect(fixture.componentInstance['searched']()).toBe(true);

    const text = fixture.nativeElement.textContent;
    expect(text).toContain('RECEPTION_CHECK_IN');
    expect(text).toContain('staff.reception');
    expect(text).toContain('Lịch hẹn');
    expect(text).toContain('Hàng đợi');
  });

  it('filters by quick-filter chips for entity types', () => {
    fixture.detectChanges();
    http.expectOne((req) => req.url === '/api/v1/admin/audit/search').flush(mockLogResponse);
    fixture.detectChanges();

    // Click quick filter chip for APPOINTMENT
    const chipAppointment = fixture.nativeElement.querySelector('[data-testid="chip-appointment"]') as HTMLButtonElement;
    chipAppointment.click();
    fixture.detectChanges();

    const reqAppt = http.expectOne((req) => req.url === '/api/v1/admin/audit/search' && req.params.get('entityType') === 'APPOINTMENT');
    expect(reqAppt.request.params.get('entityType')).toBe('APPOINTMENT');
    reqAppt.flush({
      items: [mockLogResponse.items[0]],
      page: 0,
      size: 50,
      totalElements: 1,
      totalPages: 1,
      last: true,
    });
    fixture.detectChanges();

    expect(fixture.componentInstance['items']().length).toBe(1);
    expect(fixture.componentInstance['entityType']()).toBe('APPOINTMENT');
  });

  it('searches flexibly by appointment code or identifier', () => {
    fixture.detectChanges();
    http.expectOne((req) => req.url === '/api/v1/admin/audit/search').flush(mockLogResponse);
    fixture.detectChanges();

    fixture.componentInstance['entityId'].set('LH-20260820-001');
    fixture.componentInstance['search']();

    const request = http.expectOne((req) => req.url === '/api/v1/admin/audit/search');
    expect(request.request.params.get('identifier')).toBe('LH-20260820-001');
    request.flush({
      items: [mockLogResponse.items[0]],
      page: 0,
      size: 50,
      totalElements: 1,
      totalPages: 1,
      last: true,
    });
    fixture.detectChanges();

    expect(fixture.componentInstance['items']().length).toBe(1);
  });

  it('toggles SHA-256 tamper evidence badge popover', () => {
    fixture.detectChanges();
    http.expectOne((req) => req.url === '/api/v1/admin/audit/search').flush(mockLogResponse);
    fixture.detectChanges();

    // Initial state: popover is not visible
    expect(fixture.nativeElement.querySelector('[data-testid="sha256-popover"]')).toBeNull();

    // Click SHA-256 badge on first row
    const badges = fixture.nativeElement.querySelectorAll('[data-testid="sha256-badge"]');
    expect(badges.length).toBe(2);
    (badges[0] as HTMLButtonElement).click();
    fixture.detectChanges();

    // Popover is now open showing the full hash
    const popover = fixture.nativeElement.querySelector('[data-testid="sha256-popover"]');
    expect(popover).not.toBeNull();
    expect(popover.textContent).toContain('e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855');
  });
});
