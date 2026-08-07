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

  it('shows only today booked appointments from the room QR entry point', () => {
    const today = localIsoDate();
    http.expectOne('/api/v1/rooms/NOI-01/check-in').flush({ code: 'NOI-01', name: 'Phòng Nội tổng quát 01', specialty: 'Nội tổng quát' });
    http.expectOne('/api/v1/appointments').flush([
      appointment(today, 'BOOKED', 'Nội tổng quát'),
      appointment(today, 'CANCELLED', 'Nội tổng quát'),
      appointment(today, 'BOOKED', 'Nhi khoa'),
    ]);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelectorAll('[data-testid="check-in-appointment"]').length).toBe(1);
    expect(fixture.nativeElement.querySelector('[data-testid="room-code"]').textContent).toContain('Phòng Nội');
  });

  it('returns the existing queue number after a successful check-in', () => {
    const today = localIsoDate();
    http.expectOne('/api/v1/rooms/NOI-01/check-in').flush({ code: 'NOI-01', name: 'Phòng Nội tổng quát 01', specialty: 'Nội tổng quát' });
    http.expectOne('/api/v1/appointments').flush([appointment(today, 'BOOKED', 'Nội tổng quát')]);
    fixture.detectChanges();

    const appointmentButton = fixture.nativeElement.querySelector('[data-testid="check-in-appointment"]') as HTMLButtonElement;
    appointmentButton.click();
    fixture.detectChanges();
    (fixture.nativeElement.querySelector('[data-testid="check-in-submit"]') as HTMLButtonElement).click();

    const request = http.expectOne('/api/v1/rooms/NOI-01/queue/check-in');
    expect(request.request.method).toBe('POST');
    expect(request.request.body.appointmentId).toBe('appointment-1');
    request.flush(queueTicket());
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="queue-number"]').textContent).toContain('5');
    expect(fixture.nativeElement.querySelector('[data-testid="queue-status"]').textContent).toContain('Đang chờ');
  });
});

function appointment(date: string, status: string, specialty: string) {
  return {
    id: status === 'BOOKED' && specialty === 'Nội tổng quát' ? 'appointment-1' : `${status}-${specialty}`,
    appointmentCode: 'CL-20260806-1234', specialty, doctorName: 'BS. Nguyễn An', appointmentDate: date,
    startTime: '09:00:00', reason: 'Đau đầu', status, statusLabel: status === 'BOOKED' ? 'Đã đặt' : 'Đã hủy',
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
