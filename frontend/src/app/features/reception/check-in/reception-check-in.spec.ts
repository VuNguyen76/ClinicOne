import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { ReceptionCheckIn } from './reception-check-in';

describe('ReceptionCheckIn', () => {
  let fixture: ComponentFixture<ReceptionCheckIn>;
  let http: HttpTestingController;

  beforeEach(async () => {
    sessionStorage.setItem('clinicOneStaffRole', 'RECEPTIONIST');
    await TestBed.configureTestingModule({
      imports: [ReceptionCheckIn],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();
    fixture = TestBed.createComponent(ReceptionCheckIn);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  afterEach(() => {
    http.verify();
    sessionStorage.clear();
  });

  it('searches appointments by phone and shows the patient record', () => {
    const component = fixture.componentInstance as any;
    component.query.set('0912345678');
    component.search();
    const request = http.expectOne((item) => item.url === '/api/v1/reception/appointments');
    expect(request.request.params.get('query')).toBe('0912345678');
    request.flush([appointment()]);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Nguyễn Thanh Vũ');
    expect(fixture.nativeElement.textContent).toContain('NOI-01');
  });

  it('confirms arrival and updates the queue number', () => {
    const component = fixture.componentInstance as any;
    component.query.set('CL-20260807-1234');
    component.exceptionReason.set('QR phòng bị lỗi');
    component.search();
    http.expectOne((item) => item.url === '/api/v1/reception/appointments').flush([appointment()]);
    fixture.detectChanges();

    (fixture.nativeElement.querySelector('button:not([type="submit"])') as HTMLButtonElement).click();
    const request = http.expectOne('/api/v1/reception/appointments/a-1/check-in');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ roomCode: 'NOI-01', reason: 'QR phòng bị lỗi' });
    request.flush({ ...appointment(), queueNumber: 5, queueStatus: 'WAITING', queueStatusLabel: 'Đang chờ' });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Số 05');
  });
});

function appointment() {
  return {
    id: 'a-1', appointmentCode: 'CL-20260807-1234', appointmentDate: '2026-08-07', startTime: '09:00:00',
    specialty: 'Nội tổng quát', doctorName: 'BS. Nguyễn An', roomCode: 'NOI-01', roomName: 'Phòng Nội 01',
    patientProfileId: 'p-1', patientName: 'Nguyễn Thanh Vũ', patientPhone: '0912345678', status: 'BOOKED',
    queueNumber: null, queueStatus: null, queueStatusLabel: null,
  };
}
