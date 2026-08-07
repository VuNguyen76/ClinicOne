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

    (fixture.nativeElement.querySelector('button[data-testid="check-in"]') as HTMLButtonElement).click();
    const request = http.expectOne('/api/v1/reception/appointments/a-1/check-in');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ roomCode: 'NOI-01', reason: 'QR phòng bị lỗi' });
    request.flush({ ...appointment(), queueNumber: 5, queueStatus: 'WAITING', queueStatusLabel: 'Đang chờ' });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Số 05');
  });

  it('opens the walk-in form and creates an appointment from a selected slot', () => {
    const component = fixture.componentInstance as any;
    const openButton = Array.from(fixture.nativeElement.querySelectorAll('button'))
      .find((button: any) => button.textContent.includes('Tiếp nhận không có lịch')) as HTMLButtonElement;
    openButton.click();
    fixture.detectChanges();
    http.expectOne((item) => item.url === '/api/v1/specialties').flush([
      { code: 'NOI', name: 'Nội tổng quát', description: '' },
    ]);

    component.walkInPhone.set('0912345678');
    component.loadWalkInProfiles();
    http.expectOne((item) => item.url === '/api/v1/reception/profiles').flush([
      { id: 'p-1', fullName: 'Nguyễn Thanh Vũ', relationship: 'Bản thân', primaryProfile: true },
    ]);
    component.walkInSpecialty.set('Nội tổng quát');
    component.loadWalkInSlots();
    http.expectOne((item) => item.url === '/api/v1/appointment-slots').flush([
      { specialty: 'Nội tổng quát', appointmentDate: component.walkInDate(), startTime: '09:00:00', endTime: '09:30:00', doctorName: 'BS. Nguyễn An', remainingCapacity: 1, doctorId: 'd-1', roomCode: 'NOI-01' },
    ]);
    component.walkInProfileId.set('p-1');
    component.walkInStartTime.set('09:00:00');
    component.walkInReason.set('Đau đầu từ sáng');
    component.walkInExceptionReason.set('Người bệnh đến quầy không có lịch');
    component.submitWalkIn();
    const request = http.expectOne('/api/v1/reception/walk-in');
    expect(request.request.body).toEqual({
      phone: '0912345678', profileId: 'p-1', doctorId: 'd-1', appointmentDate: component.walkInDate(),
      startTime: '09:00:00', reason: 'Đau đầu từ sáng', exceptionReason: 'Người bệnh đến quầy không có lịch',
    });
    request.flush({ ...appointment(), queueNumber: 8, queueStatus: 'WAITING', queueStatusLabel: 'Đang chờ' });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Đã tạo lịch và cấp số 08');
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
