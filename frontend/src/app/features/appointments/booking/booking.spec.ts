import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { Booking } from './booking';

describe('Booking calendar', () => {
  let fixture: ComponentFixture<Booking>;
  let component: Booking;
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Booking],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(Booking);
    component = fixture.componentInstance;
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    http.expectOne('/api/v1/specialties').flush([{ code: 'NOI', name: 'Nội tổng quát', description: 'Khám tổng quát' }]);
    http.expectOne('/api/v1/services').flush([]);
    http.expectOne('/api/v1/patient-profiles').flush([]);
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  it('renders a complete six-week calendar grid instead of a short date strip', () => {
    expect(component['dates']().length).toBe(42);
    component['chooseSpecialty']({ code: 'NOI', name: 'Nội tổng quát', description: 'Khám tổng quát' });
    http.expectOne((item) => item.url === '/api/v1/appointment-slots').flush([]);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelectorAll('[data-testid="calendar-date"]').length).toBe(42);
  });

  it('loads availability for the complete displayed month', () => {
    component['chooseSpecialty']({ code: 'NOI', name: 'Nội tổng quát', description: 'Khám tổng quát' });
    const request = http.expectOne((item) => item.url === '/api/v1/appointment-slots' && item.params.get('specialty') === 'Nội tổng quát');
    const month = component['calendarMonth']();
    const expectedFrom = `${month.getFullYear()}-${String(month.getMonth() + 1).padStart(2, '0')}-01`;
    const end = new Date(month.getFullYear(), month.getMonth() + 1, 0);
    const expectedTo = `${end.getFullYear()}-${String(end.getMonth() + 1).padStart(2, '0')}-${String(end.getDate()).padStart(2, '0')}`;
    expect(request.request.params.get('from')).toBe(expectedFrom);
    expect(request.request.params.get('to')).toBe(expectedTo);
    request.flush([]);
  });

  it('ignores a late response from a month that is no longer displayed', () => {
    component['chooseSpecialty']({ code: 'NOI', name: 'Ná»™i tá»•ng quÃ¡t', description: 'KhÃ¡m tá»•ng quÃ¡t' });
    const firstRequest = http.expectOne((item) => item.url === '/api/v1/appointment-slots');

    component['nextMonth']();
    const secondRequest = http.expectOne((item) => item.url === '/api/v1/appointment-slots');
    const displayedMonth = component['calendarMonth']();
    const displayedDate = `${displayedMonth.getFullYear()}-${String(displayedMonth.getMonth() + 1).padStart(2, '0')}-02`;

    secondRequest.flush([{
      specialty: 'Ná»™i tá»•ng quÃ¡t', appointmentDate: displayedDate, startTime: '09:00:00', endTime: '09:30:00',
      doctorName: 'BÃ¡c sÄ© thÃ¡ng hiá»‡n táº¡i', remainingCapacity: 1,
    }]);
    firstRequest.flush([{
      specialty: 'Ná»™i tá»•ng quÃ¡t', appointmentDate: '2099-01-01', startTime: '08:00:00', endTime: '08:30:00',
      doctorName: 'BÃ¡c sÄ© thÃ¡ng cÅ©', remainingCapacity: 1,
    }]);

    expect(component['monthSlots']()).toHaveLength(1);
    expect(component['monthSlots']()[0].doctorName).toBe('BÃ¡c sÄ© thÃ¡ng hiá»‡n táº¡i');
  });

  it('uses the already loaded month data when a date is selected', () => {
    component['chooseSpecialty']({ code: 'NOI', name: 'Nội tổng quát', description: 'Khám tổng quát' });
    const request = http.expectOne((item) => item.url === '/api/v1/appointment-slots');
    const date = component['dates']().find((item) => item.inCurrentMonth && item.iso >= component['today']);
    expect(date).toBeDefined();
    request.flush([{
      specialty: 'Nội tổng quát',
      appointmentDate: date!.iso,
      startTime: '08:30:00',
      endTime: '09:30:00',
      doctorName: 'Bác sĩ chuyên khoa',
      remainingCapacity: 9,
    }]);

    component['chooseDate'](date!);

    expect(component['availableSlots']()).toHaveLength(1);
    expect(component['availableSlots']()[0].label).toBe('08:30 - 09:30');
    http.expectNone((item) => item.url === '/api/v1/appointment-slots');
  });

  it('holds the selected slot before opening the patient details step', () => {
    component['chooseSpecialty']({ code: 'NOI', name: 'Nội tổng quát', description: 'Khám tổng quát' });
    const slotsRequest = http.expectOne((item) => item.url === '/api/v1/appointment-slots');
    const date = component['dates']().find((item) => item.inCurrentMonth && item.iso >= component['today']);
    slotsRequest.flush([{
      specialty: 'Nội tổng quát', appointmentDate: date!.iso, startTime: '08:30:00', endTime: '09:30:00',
      doctorName: 'Bác sĩ chuyên khoa', remainingCapacity: 1, doctorId: 'doctor-1', roomCode: 'NOI-01',
    }]);
    component['chooseDate'](date!);
    component['chooseSlot'](component['availableSlots']()[0]);

    component['continueToDetails']();
    const holdRequest = http.expectOne('/api/v1/appointment-holds');
    expect(holdRequest.request.body).toEqual({
      specialty: 'Nội tổng quát', doctorName: 'Bác sĩ chuyên khoa', doctorId: 'doctor-1',
      appointmentDate: date!.iso, startTime: '08:30',
    });
    holdRequest.flush({
      id: 'hold-1', specialty: 'Nội tổng quát', doctorName: 'Bác sĩ chuyên khoa',
      appointmentDate: date!.iso, startTime: '08:30:00', expiresAt: '2026-08-10T01:05:00Z',
    });

    expect(component['holdId']()).toBe('hold-1');
    expect(component['step']()).toBe(3);
  });

  it('lets the patient choose a configured service and carries its id into the hold', () => {
    component['chooseClinicService']({
      id: 'service-1', name: 'Khám tổng quát cơ bản', specialty: 'Nội tổng quát',
      visitType: 'Khám thường', durationMinutes: 30, active: true, eligibleDoctors: [],
    });
    const slotsRequest = http.expectOne((item) => item.url === '/api/v1/appointment-slots');
    expect(slotsRequest.request.params.get('serviceId')).toBe('service-1');
    const date = component['dates']().find((item) => item.inCurrentMonth && item.iso >= component['today']);
    slotsRequest.flush([{
      specialty: 'Nội tổng quát', appointmentDate: date!.iso, startTime: '08:30:00', endTime: '09:00:00',
      doctorName: 'Bác sĩ chuyên khoa', remainingCapacity: 1, doctorId: 'doctor-1', roomCode: 'NOI-01',
    }]);
    component['chooseDate'](date!);
    component['chooseSlot'](component['availableSlots']()[0]);
    component['continueToDetails']();

    const holdRequest = http.expectOne('/api/v1/appointment-holds');
    expect(holdRequest.request.body.serviceId).toBe('service-1');
  });
});
