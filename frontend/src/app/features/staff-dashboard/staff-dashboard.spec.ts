import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { StaffDashboard } from './staff-dashboard';

describe('StaffDashboard', () => {
  let fixture: ComponentFixture<StaffDashboard>;
  let http: HttpTestingController;

  beforeEach(async () => {
    sessionStorage.setItem('clinicOneStaffRole', 'COORDINATOR');
    await TestBed.configureTestingModule({
      imports: [StaffDashboard],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(StaffDashboard);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  afterEach(() => {
    http.verify();
    sessionStorage.clear();
  });

  it('loads rooms first and only requests the selected room queue', () => {
    http.expectOne('/api/v1/rooms').flush([room('NOI-01')]);
    const queueRequest = http.expectOne((request) => request.url === '/api/v1/rooms/NOI-01/queue');
    expect(queueRequest.request.params.get('date')).toMatch(/^\d{4}-\d{2}-\d{2}$/);
    queueRequest.flush([ticket('ticket-1', 'WAITING')]);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelectorAll('[data-testid="queue-row"]').length).toBe(1);
    expect(fixture.nativeElement.querySelector('[data-testid="waiting-count"]').textContent).toContain('1');
  });

  it('moves a waiting ticket when the coordinator calls the number', () => {
    http.expectOne('/api/v1/rooms').flush([room('NOI-01')]);
    http.expectOne((request) => request.url.endsWith('/queue')).flush([ticket('ticket-1', 'WAITING')]);
    fixture.detectChanges();

    (fixture.nativeElement.querySelector('[data-testid="call-ticket"]') as HTMLButtonElement).click();
    const action = http.expectOne('/api/v1/queue/ticket-1/call');
    expect(action.request.method).toBe('POST');
    action.flush(ticket('ticket-1', 'CALLED'));
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="queue-status"]').textContent).toContain('Đã gọi');
  });
});

function room(code: string) {
  return { id: `${code}-id`, code, name: `Phòng ${code}`, specialty: 'Nội tổng quát', active: true };
}

function ticket(id: string, status: string) {
  const labels: Record<string, string> = { WAITING: 'Đang chờ', CALLED: 'Đã gọi' };
  return {
    id,
    queueNumber: 1,
    roomCode: 'NOI-01',
    roomName: 'Phòng NOI-01',
    queueDate: '2026-08-06',
    appointmentTime: '09:00:00',
    status,
    statusLabel: labels[status] ?? status,
    appointmentCode: 'CLN-0001',
    specialty: 'Nội tổng quát',
    doctorName: 'BS. Nguyễn An',
  };
}
