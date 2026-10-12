import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute } from '@angular/router';
import { QueueCheckIn } from './queue-check-in';

describe('QueueCheckIn', () => {
  let fixture: ComponentFixture<QueueCheckIn>;
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [QueueCheckIn],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: { get: () => 'NOI-01' } } } },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(QueueCheckIn);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  it('asks the patient to choose a profile when multiple matching profiles have appointments', () => {
    const today = localIsoDate();
    http.expectOne('/api/v1/rooms/NOI-01/check-in').flush({ code: 'NOI-01', name: 'Phòng Nội tổng quát 01', specialty: 'Nội tổng quát' });
    http.expectOne('/api/v1/rooms/NOI-01/check-in/appointments').flush([
      appointment(today, 'BOOKED', 'Nội tổng quát', 'profile-1'),
      appointment(today, 'BOOKED', 'Nội tổng quát', 'profile-2'),
    ]);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelectorAll('[data-testid="check-in-profile"]').length).toBe(2);
    http.expectNone('/api/v1/rooms/NOI-01/queue/check-in');
    expect(fixture.nativeElement.querySelector('[data-testid="room-code"]').textContent).toContain('Phòng Nội');
  });

  it('checks in automatically when exactly one matching appointment exists', () => {
    const today = localIsoDate();
    http.expectOne('/api/v1/rooms/NOI-01/check-in').flush({ code: 'NOI-01', name: 'Phòng Nội tổng quát 01', specialty: 'Nội tổng quát' });
    http.expectOne('/api/v1/rooms/NOI-01/check-in/appointments').flush([
      appointment(today, 'BOOKED', 'Nội tổng quát', 'profile-1'),
    ]);

    const request = http.expectOne('/api/v1/rooms/NOI-01/queue/check-in');
    expect(request.request.headers.get('Idempotency-Key')).toMatch(/^checkin-/);
    expect(request.request.method).toBe('POST');
    expect(request.request.body.appointmentId).toBe('appointment-profile-1');
    request.flush(queueTicket());
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="queue-number"]').textContent).toContain('5');
    expect(fixture.nativeElement.querySelector('[data-testid="queue-status"]').textContent).toContain('Đang chờ');
  });

  it('keeps a checked-in appointment available so a returning patient can scan again', () => {
    const today = localIsoDate();
    http.expectOne('/api/v1/rooms/NOI-01/check-in').flush({ code: 'NOI-01', name: 'Phòng Nội tổng quát 01', specialty: 'Nội tổng quát' });
    http.expectOne('/api/v1/rooms/NOI-01/check-in/appointments').flush([
      appointment(today, 'CHECKED_IN', 'Nội tổng quát', 'profile-1'),
    ]);

    const request = http.expectOne('/api/v1/rooms/NOI-01/queue/check-in');
    request.flush({ ...queueTicket(), presenceStatus: 'RETURN_REQUIRED', presenceLabel: 'Chờ quay lại' });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('không cấp số mới');
  });

  it('shows the existing queue result when the appointment is already completed', () => {
    const today = localIsoDate();
    http.expectOne('/api/v1/rooms/NOI-01/check-in').flush({ code: 'NOI-01', name: 'Phòng Nội tổng quát 01', specialty: 'Nội tổng quát' });
    http.expectOne('/api/v1/rooms/NOI-01/check-in/appointments').flush([
      appointment(today, 'COMPLETED', 'Nội tổng quát', 'profile-1'),
    ]);

    const request = http.expectOne('/api/v1/rooms/NOI-01/queue/check-in');
    request.flush({ ...queueTicket(), status: 'COMPLETED', statusLabel: 'Đã hoàn tất' });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="queue-number"]').textContent).toContain('5');
    expect(fixture.nativeElement.querySelector('[data-testid="queue-status"]').textContent).toContain('Đã hoàn tất');
  });
});

function appointment(date: string, status: string, specialty: string, profileId: string) {
  return {
    id: `appointment-${profileId}`,
    appointmentCode: 'CL-20260806-1234', specialty, doctorName: 'BS. Nguyễn An', appointmentDate: date,
    startTime: '23:59:00', reason: 'Đau đầu', status, profileId, profileName: `Hồ sơ ${profileId}`,
    statusLabel: status === 'BOOKED' ? 'Đã đặt' : status === 'CHECKED_IN' ? 'Đã check-in' : 'Đã hoàn tất',
  };
}

function queueTicket() {
  return {
    id: 'ticket-1', queueNumber: 5, roomCode: 'NOI-01', roomName: 'Phòng Nội tổng quát 01',
    queueDate: localIsoDate(), appointmentTime: '09:00:00', status: 'WAITING', statusLabel: 'Đang chờ',
    appointmentCode: 'CL-20260806-1234', specialty: 'Nội tổng quát', doctorName: 'BS. Nguyễn An',
  };
}

function localIsoDate(): string {
  const today = new Date();
  return `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, '0')}-${String(today.getDate()).padStart(2, '0')}`;
}
