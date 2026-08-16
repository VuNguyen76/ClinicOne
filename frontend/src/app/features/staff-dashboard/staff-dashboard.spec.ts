import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { vi } from 'vitest';
import { StaffDashboard } from './staff-dashboard';

describe('StaffDashboard', () => {
  let fixture: ComponentFixture<StaffDashboard>;
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [StaffDashboard],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([
        { path: 'doctor/examinations/:ticketId', component: StaffDashboard },
      ])],
    }).compileComponents();
  });

  afterEach(() => {
    fixture?.destroy();
    http.verify();
    sessionStorage.clear();
    vi.useRealTimers();
  });

  it('loads rooms first and only requests the selected room queue', () => {
    createDashboard('COORDINATOR');
    http.expectOne('/api/v1/rooms').flush([room('NOI-01')]);
    const queueRequest = http.expectOne((request) => request.url === '/api/v1/rooms/NOI-01/queue');
    expect(queueRequest.request.params.get('date')).toMatch(/^\d{4}-\d{2}-\d{2}$/);
    queueRequest.flush([ticket('ticket-1', 'WAITING')]);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelectorAll('[data-testid="queue-row"]').length).toBe(1);
    expect(fixture.nativeElement.querySelector('[data-testid="waiting-count"]').textContent).toContain('1');
  });

  it('keeps a coordinator view read-only for waiting tickets', () => {
    createDashboard('COORDINATOR');
    http.expectOne('/api/v1/rooms').flush([room('NOI-01')]);
    http.expectOne((request) => request.url.endsWith('/queue')).flush([ticket('ticket-1', 'WAITING')]);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="call-ticket"]')).toBeNull();
  });

  it('shows the call-next action to a doctor for a waiting ticket', () => {
    createDashboard('COORDINATOR');
    http.expectOne('/api/v1/rooms').flush([room('NOI-01')]);
    http.expectOne((request) => request.url.endsWith('/queue')).flush([ticket('ticket-1', 'WAITING')]);
    const component = fixture.componentInstance as any;
    component.role.set('DOCTOR');
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="call-next"]')).not.toBeNull();
  });

  it('uses the current assigned shift without asking a doctor to choose a date', () => {
    createDashboard('DOCTOR');
    http.expectOne((candidate) => candidate.url === '/api/v1/doctor/queue')
      .flush(doctorQueue([ticket('ticket-1', 'WAITING')]));
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="date-selector"]')).toBeNull();
    expect(fixture.nativeElement.textContent).toContain('Ca khám hiện tại');
  });

  it('locks doctor actions and explains when there is no current shift', () => {
    createDashboard('DOCTOR');
    http.expectOne((candidate) => candidate.url === '/api/v1/doctor/queue').flush({
      ...doctorQueue([]), shiftStatus: 'NONE',
    });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Không có ca làm việc');
    expect(fixture.nativeElement.querySelector('[data-testid="call-next"]')).toBeNull();
  });

  it('shows patient identity before a doctor starts the called examination', () => {
    createDashboard('DOCTOR');
    http.expectOne((candidate) => candidate.url === '/api/v1/doctor/queue')
      .flush(doctorQueue([ticket('ticket-1', 'CALLED')]));
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Nguyễn Thanh Vũ');
    expect(fixture.nativeElement.textContent).toContain('07/06/2005');
  });

  it('starts an examination with one retry-safe request key', () => {
    createDashboard('DOCTOR');
    http.expectOne((candidate) => candidate.url === '/api/v1/doctor/queue')
      .flush(doctorQueue([ticket('ticket-1', 'CALLED')]));
    const component = fixture.componentInstance as any;

    component.act(ticket('ticket-1', 'CALLED'), 'start');

    const request = http.expectOne('/api/v1/doctor/examinations/ticket-1/start');
    expect(request.request.headers.get('Idempotency-Key')).toMatch(/^start-/);
    request.flush({ ticketId: 'ticket-1', status: 'IN_PROGRESS' });
  });

  it('does not expose manual completion to a doctor while a visit is in service', () => {
    createDashboard('COORDINATOR');
    http.expectOne('/api/v1/rooms').flush([room('NOI-01')]);
    http.expectOne((request) => request.url.endsWith('/queue')).flush([ticket('ticket-1', 'IN_SERVICE')]);
    const component = fixture.componentInstance as any;
    component.role.set('DOCTOR');
    fixture.detectChanges();

    const buttons = Array.from(fixture.nativeElement.querySelectorAll('button')) as HTMLButtonElement[];
    expect(buttons.some((button) => button.textContent?.includes('Hoàn tất'))).toBe(false);
  });

  it('does not expose clinical start or completion actions to a coordinator', () => {
    createDashboard('COORDINATOR');
    http.expectOne('/api/v1/rooms').flush([room('NOI-01')]);
    http.expectOne((request) => request.url.endsWith('/queue')).flush([ticket('ticket-1', 'IN_SERVICE')]);
    fixture.detectChanges();

    const buttons = Array.from(fixture.nativeElement.querySelectorAll('button')) as HTMLButtonElement[];
    expect(buttons.some((button) => button.textContent?.includes('Vào khám'))).toBe(false);
    expect(buttons.some((button) => button.textContent?.includes('Hoàn tất'))).toBe(false);
  });

  it('keeps the priority order returned for the doctor queue', () => {
    createDashboard('DOCTOR');
    const request = http.expectOne((candidate) => candidate.url === '/api/v1/doctor/queue');
    request.flush({
      roomCode: 'NOI-01', roomName: 'Phòng NOI-01', specialty: 'Nội tổng quát',
      tickets: [ticket('priority', 'WAITING', 2, true), ticket('normal', 'WAITING', 1, false)],
    });
    fixture.detectChanges();

    const rows = fixture.nativeElement.querySelectorAll('[data-testid="queue-row"]') as NodeListOf<HTMLTableRowElement>;
    const numbers = Array.from(rows)
      .map((row) => row.querySelector('td:first-child span')?.textContent?.trim());
    expect(numbers).toEqual(['2', '1']);
  });

  it('refreshes a doctor queue every three seconds without manual input', () => {
    vi.useFakeTimers();
    createDashboard('DOCTOR');
    http.expectOne((candidate) => candidate.url === '/api/v1/doctor/queue')
      .flush(doctorQueue([ticket('first', 'WAITING', 1)]));

    vi.advanceTimersByTime(3_000);
    http.expectOne((candidate) => candidate.url === '/api/v1/doctor/queue')
      .flush(doctorQueue([ticket('next', 'WAITING', 2)]));
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="queue-row"] td:first-child span')?.textContent?.trim())
      .toBe('2');
  });

  it('does not start another automatic refresh while the previous request is still pending', () => {
    vi.useFakeTimers();
    createDashboard('DOCTOR');
    http.expectOne((candidate) => candidate.url === '/api/v1/doctor/queue')
      .flush(doctorQueue([ticket('first', 'WAITING', 1)]));

    vi.advanceTimersByTime(3_000);
    const pendingRefresh = http.expectOne((candidate) => candidate.url === '/api/v1/doctor/queue');
    vi.advanceTimersByTime(3_000);
    http.expectNone((candidate) => candidate.url === '/api/v1/doctor/queue');
    pendingRefresh.flush(doctorQueue([ticket('first', 'WAITING', 1)]));
  });

  it('warns a doctor after ten seconds without a successful queue update', () => {
    vi.useFakeTimers();
    createDashboard('DOCTOR');
    http.expectOne((candidate) => candidate.url === '/api/v1/doctor/queue')
      .flush(doctorQueue([ticket('first', 'WAITING', 1)]));

    for (const elapsed of [3_000, 3_000, 3_000]) {
      vi.advanceTimersByTime(elapsed);
      http.expectOne((candidate) => candidate.url === '/api/v1/doctor/queue')
        .flush({ error: { message: 'Tạm thời không kết nối được' } }, { status: 503, statusText: 'Service Unavailable' });
    }
    vi.advanceTimersByTime(1_001);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="queue-sync-warning"]')).not.toBeNull();
  });

  function createDashboard(role: string): void {
    sessionStorage.setItem('clinicOneStaffRole', role);
    fixture = TestBed.createComponent(StaffDashboard);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  }
});

function doctorQueue(tickets: ReturnType<typeof ticket>[]) {
  return { roomCode: 'NOI-01', roomName: 'Phòng NOI-01', specialty: 'Nội tổng quát', shiftStatus: 'ACTIVE', tickets };
}

function room(code: string) {
  return { id: `${code}-id`, code, name: `Phòng ${code}`, specialty: 'Nội tổng quát', active: true };
}

function ticket(id: string, status: string, queueNumber = 1, priority = false) {
  const labels: Record<string, string> = { WAITING: 'Đang chờ', CALLED: 'Đã gọi', IN_SERVICE: 'Đang khám' };
  return {
    id,
    queueNumber,
    roomCode: 'NOI-01',
    roomName: 'Phòng NOI-01',
    queueDate: '2026-08-06',
    appointmentTime: '09:00:00',
    status,
    statusLabel: labels[status] ?? status,
    appointmentCode: 'CLN-0001',
    specialty: 'Nội tổng quát',
    doctorName: 'BS. Nguyễn An',
    patientName: 'Nguyễn Thanh Vũ',
    patientDateOfBirth: '2005-06-07',
    priority,
  };
}
