import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute } from '@angular/router';
import { QueueBoard } from './queue-board';

describe('QueueBoard', () => {
  let fixture: ComponentFixture<QueueBoard>;
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [QueueBoard],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: { get: () => 'NOI-01' } } } },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(QueueBoard);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  it('loads the room queue for the current day', () => {
    const request = http.expectOne((item) => item.url === '/api/v1/rooms/NOI-01/queue');
    expect(request.request.params.get('date')).toMatch(/^\d{4}-\d{2}-\d{2}$/);
    request.flush([ticket('WAITING', 'Đang chờ')]);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="queue-row"]').textContent).toContain('05');
    expect(fixture.nativeElement.querySelector('[data-testid="queue-summary"]').textContent).toContain('1 lượt');
  });

  it('calls a waiting ticket and refreshes its state', () => {
    http.expectOne((item) => item.url === '/api/v1/rooms/NOI-01/queue').flush([ticket('WAITING', 'Đang chờ')]);
    fixture.detectChanges();
    (fixture.nativeElement.querySelector('[data-testid="call-ticket"]') as HTMLButtonElement).click();

    const request = http.expectOne('/api/v1/queue/ticket-1/call');
    expect(request.request.method).toBe('POST');
    request.flush(ticket('CALLED', 'Đang được gọi'));
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="queue-status"]').textContent).toContain('Đang được gọi');
  });
});

function ticket(status: string, statusLabel: string) {
  return {
    id: 'ticket-1', queueNumber: 5, roomCode: 'NOI-01', roomName: 'Phòng Nội tổng quát 01',
    queueDate: new Date().toISOString().slice(0, 10), appointmentTime: '09:00:00', status, statusLabel,
    appointmentCode: 'CL-20260806-1234', specialty: 'Nội tổng quát', doctorName: 'BS. Nguyễn An',
  };
}
