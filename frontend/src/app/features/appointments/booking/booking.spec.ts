import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { vi } from 'vitest';
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

  it('keeps the patient session for a business-rule 403 instead of redirecting to login', () => {
    const sessionToken = 'valid-patient-session';
    sessionStorage.setItem('clinicOneAccessToken', sessionToken);
    const navigate = vi.spyOn(component['router'], 'navigateByUrl').mockResolvedValue(true);

    component['handleAuthError']({
      status: 403,
      error: { code: 'ACCOUNT_ACTIVATION_REQUIRED', message: 'Kích hoạt tài khoản trước khi đặt lịch.' },
    });

    expect(sessionStorage.getItem('clinicOneAccessToken')).toBe(sessionToken);
    expect(navigate).not.toHaveBeenCalled();
    expect(component['error']).toContain('Kích hoạt tài khoản trước khi đặt lịch.');
  });

  it('clears the session and redirects when authentication really expired', () => {
    sessionStorage.setItem('clinicOneAccessToken', 'expired-session');
    const navigate = vi.spyOn(component['router'], 'navigateByUrl').mockResolvedValue(true);

    component['handleAuthError']({ status: 401, error: { message: 'Phiên hết hạn.' } });

    expect(sessionStorage.getItem('clinicOneAccessToken')).toBeNull();
    expect(navigate).toHaveBeenCalledWith('/login');
  });

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

  it('clamps the final month request and disables navigation beyond the thirty-day window', () => {
    component['chooseSpecialty']({ code: 'NOI', name: 'Nội tổng quát', description: 'Khám tổng quát' });
    const firstRequest = http.expectOne((item) => item.url === '/api/v1/appointment-slots');
    firstRequest.flush([]);

    component['nextMonth']();
    const secondRequest = http.expectOne((item) => item.url === '/api/v1/appointment-slots');
    const expectedEnd = component['bookingWindowEndIso']();
    expect(secondRequest.request.params.get('to')).toBe(expectedEnd);
    expect(component['isNextMonthDisabled']()).toBe(true);
    secondRequest.flush([]);
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
      appointmentDate: date!.iso, startTime: '08:30', profileId: null, serviceId: undefined,
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

  it('sends profileId in hold request when a profile is selected', () => {
    const profileId = 'profile-sub-1';
    component['patientProfiles'].set([
      { id: 'profile-primary', fullName: 'Bản thân', primaryProfile: true, active: true } as any,
      { id: profileId, fullName: 'Con trai', primaryProfile: false, active: true } as any,
    ]);
    component['form'].controls.profileId.setValue(profileId);

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
    expect(holdRequest.request.body.profileId).toBe(profileId);
    holdRequest.flush({
      id: 'hold-profile', specialty: 'Nội tổng quát', doctorName: 'Bác sĩ chuyên khoa',
      appointmentDate: date!.iso, startTime: '08:30:00', expiresAt: '2026-08-10T01:05:00Z',
    });
  });

  it('re-holds the selected slot when the patient changes profile on the details step', () => {
    const profile1Id = 'profile-primary';
    const profile2Id = 'profile-sub-change';
    component['patientProfiles'].set([
      { id: profile1Id, fullName: 'Bản thân', primaryProfile: true, active: true } as any,
      { id: profile2Id, fullName: 'Con', primaryProfile: false, active: true } as any,
    ]);
    component['form'].controls.profileId.setValue(profile1Id);

    component['chooseSpecialty']({ code: 'NOI', name: 'Nội tổng quát', description: 'Khám tổng quát' });
    const slotsRequest = http.expectOne((item) => item.url === '/api/v1/appointment-slots');
    const date = component['dates']().find((item) => item.inCurrentMonth && item.iso >= component['today']);
    slotsRequest.flush([{
      specialty: 'Nội tổng quát', appointmentDate: date!.iso, startTime: '08:30:00', endTime: '09:30:00',
      doctorName: 'Bác sĩ An', remainingCapacity: 1, doctorId: 'doctor-1', roomCode: 'NOI-01',
    }]);
    component['chooseDate'](date!);
    component['chooseSlot'](component['availableSlots']()[0]);
    component['continueToDetails']();

    http.expectOne('/api/v1/appointment-holds').flush({
      id: 'hold-profile-1', specialty: 'Nội tổng quát', doctorName: 'Bác sĩ An',
      appointmentDate: date!.iso, startTime: '08:30:00', expiresAt: '2026-08-10T01:05:00Z',
    });
    component['form'].controls.reason.setValue('Đau đầu');

    component['onProfileChange']({ target: { value: profile2Id } } as unknown as Event);
    const replacementHold = http.expectOne('/api/v1/appointment-holds');
    expect(replacementHold.request.body.profileId).toBe(profile2Id);
    replacementHold.flush({
      id: 'hold-profile-2', specialty: 'Nội tổng quát', doctorName: 'Bác sĩ An',
      appointmentDate: date!.iso, startTime: '08:30:00', expiresAt: '2026-08-10T01:05:00Z',
    });

    vi.spyOn(component['router'], 'navigateByUrl').mockResolvedValue(true);
    component['submit']();
    const createRequest = http.expectOne('/api/v1/appointments');
    expect(createRequest.request.body.profileId).toBe(profile2Id);
    expect(createRequest.request.body.holdId).toBe('hold-profile-2');
    createRequest.flush({});
  });

  it('reuses an idempotency key for an identical retry and creates a new key when the booking payload changes', () => {
    component['chooseSpecialty']({ code: 'NOI', name: 'Nội tổng quát', description: 'Khám tổng quát' });
    const slotsRequest = http.expectOne((item) => item.url === '/api/v1/appointment-slots');
    const date = component['dates']().find((item) => item.inCurrentMonth && item.iso >= component['today']);
    slotsRequest.flush([{
      specialty: 'Nội tổng quát', appointmentDate: date!.iso, startTime: '08:30:00', endTime: '09:30:00',
      doctorName: 'Bác sĩ An', remainingCapacity: 1, doctorId: 'doctor-1', roomCode: 'NOI-01',
    }]);
    component['chooseDate'](date!);
    component['chooseSlot'](component['availableSlots']()[0]);
    component['continueToDetails']();
    http.expectOne('/api/v1/appointment-holds').flush({
      id: 'hold-1', specialty: 'Nội tổng quát', doctorName: 'Bác sĩ An',
      appointmentDate: date!.iso, startTime: '08:30:00', expiresAt: '2026-08-10T01:05:00Z',
    });
    component['form'].controls.reason.setValue('Đau đầu');

    component['submit']();
    const firstRequest = http.expectOne('/api/v1/appointments');
    const firstKey = firstRequest.request.headers.get('Idempotency-Key');
    expect(firstKey).toBeTruthy();
    component['submit']();
    http.expectNone('/api/v1/appointments');
    firstRequest.flush({ message: 'Tạm thời không thể lưu' }, { status: 503, statusText: 'Service Unavailable' });

    component['submit']();
    const identicalRetry = http.expectOne('/api/v1/appointments');
    expect(identicalRetry.request.headers.get('Idempotency-Key')).toBe(firstKey);
    identicalRetry.flush({ message: 'Tạm thời không thể lưu' }, { status: 503, statusText: 'Service Unavailable' });

    component['form'].controls.reason.setValue('Đau đầu kéo dài');
    component['submit']();
    const changedRequest = http.expectOne('/api/v1/appointments');
    expect(changedRequest.request.headers.get('Idempotency-Key')).not.toBe(firstKey);
    vi.spyOn(component['router'], 'navigateByUrl').mockResolvedValue(true);
    changedRequest.flush({});
  });

  it('keeps the selected slot stable while its hold request is pending', () => {
    component['chooseSpecialty']({ code: 'NOI', name: 'Nội tổng quát', description: 'Khám tổng quát' });
    const slotsRequest = http.expectOne((item) => item.url === '/api/v1/appointment-slots');
    const date = component['dates']().find((item) => item.inCurrentMonth && item.iso >= component['today']);
    slotsRequest.flush([
      {
        specialty: 'Nội tổng quát', appointmentDate: date!.iso, startTime: '08:30:00', endTime: '09:30:00',
        doctorName: 'Bác sĩ An', remainingCapacity: 1, doctorId: 'doctor-1', roomCode: 'NOI-01',
      },
      {
        specialty: 'Nội tổng quát', appointmentDate: date!.iso, startTime: '09:30:00', endTime: '10:30:00',
        doctorName: 'Bác sĩ An', remainingCapacity: 1, doctorId: 'doctor-1', roomCode: 'NOI-01',
      },
    ]);
    component['chooseDate'](date!);
    component['chooseSlot'](component['availableSlots']()[0]);
    component['continueToDetails']();
    const obsoleteHold = http.expectOne('/api/v1/appointment-holds');

    component['chooseSlot'](component['availableSlots']()[1]);
    expect(component['selectedSlot']()).toBe('doctor-1|08:30');
    expect(component['form'].controls.startTime.value).toBe('08:30');
    obsoleteHold.flush({
      id: 'hold-1', specialty: 'Nội tổng quát', doctorName: 'Bác sĩ An',
      appointmentDate: date!.iso, startTime: '08:30:00', expiresAt: '2026-08-10T01:05:00Z',
    });

    expect(component['selectedSlot']()).toBe('doctor-1|08:30');
    expect(component['form'].controls.startTime.value).toBe('08:30');
    expect(component['holdId']()).toBe('hold-1');
    expect(component['holdBusy']()).toBe(false);
    expect(component['step']()).toBe(3);
  });

  it('allows booking same slot for different profile', () => {
    const profile1Id = 'profile-primary';
    const profile2Id = 'profile-sub-2';
    component['patientProfiles'].set([
      { id: profile1Id, fullName: 'Bản thân', primaryProfile: true, active: true } as any,
      { id: profile2Id, fullName: 'Vợ', primaryProfile: false, active: true } as any,
    ]);
    component['form'].controls.profileId.setValue(profile1Id);

    component['chooseSpecialty']({ code: 'NOI', name: 'Nội tổng quát', description: 'Khám tổng quát' });
    const slotsRequest = http.expectOne((item) => item.url === '/api/v1/appointment-slots');
    const date = component['dates']().find((item) => item.inCurrentMonth && item.iso >= component['today']);
    slotsRequest.flush([{
      specialty: 'Nội tổng quát', appointmentDate: date!.iso, startTime: '08:30:00', endTime: '09:30:00',
      doctorName: 'Bác sĩ chuyên khoa', remainingCapacity: 2, doctorId: 'doctor-1', roomCode: 'NOI-01',
    }]);

    component['chooseDate'](date!);
    component['chooseSlot'](component['availableSlots']()[0]);
    component['continueToDetails']();
    const hold1 = http.expectOne('/api/v1/appointment-holds');
    expect(hold1.request.body.profileId).toBe(profile1Id);
    hold1.flush({
      id: 'hold-1', specialty: 'Nội tổng quát', doctorName: 'Bác sĩ chuyên khoa',
      appointmentDate: date!.iso, startTime: '08:30:00', expiresAt: '2026-08-10T01:05:00Z',
    });

    component['back']();
    component['form'].controls.profileId.setValue(profile2Id);
    component['clearHold']();
    component['chooseDate'](date!);
    component['chooseSlot'](component['availableSlots']()[0]);
    component['continueToDetails']();
    const hold2 = http.expectOne('/api/v1/appointment-holds');
    expect(hold2.request.body.profileId).toBe(profile2Id);
    hold2.flush({
      id: 'hold-2', specialty: 'Nội tổng quát', doctorName: 'Bác sĩ chuyên khoa',
      appointmentDate: date!.iso, startTime: '08:30:00', expiresAt: '2026-08-10T01:05:00Z',
    });

    expect(component['holdId']()).toBe('hold-2');
  });

  it('creates new hold when profile changes without explicit clearHold', () => {
    const profile1Id = 'profile-primary';
    const profile2Id = 'profile-sub-3';
    component['patientProfiles'].set([
      { id: profile1Id, fullName: 'Bản thân', primaryProfile: true, active: true } as any,
      { id: profile2Id, fullName: 'Con gái', primaryProfile: false, active: true } as any,
    ]);
    component['form'].controls.profileId.setValue(profile1Id);

    component['chooseSpecialty']({ code: 'NOI', name: 'Nội tổng quát', description: 'Khám tổng quát' });
    const slotsRequest = http.expectOne((item) => item.url === '/api/v1/appointment-slots');
    const date = component['dates']().find((item) => item.inCurrentMonth && item.iso >= component['today']);
    slotsRequest.flush([{
      specialty: 'Nội tổng quát', appointmentDate: date!.iso, startTime: '08:30:00', endTime: '09:30:00',
      doctorName: 'Bác sĩ chuyên khoa', remainingCapacity: 2, doctorId: 'doctor-1', roomCode: 'NOI-01',
    }]);

    component['chooseDate'](date!);
    component['chooseSlot'](component['availableSlots']()[0]);
    component['continueToDetails']();
    const hold1 = http.expectOne('/api/v1/appointment-holds');
    expect(hold1.request.body.profileId).toBe(profile1Id);
    hold1.flush({
      id: 'hold-1', specialty: 'Nội tổng quát', doctorName: 'Bác sĩ chuyên khoa',
      appointmentDate: date!.iso, startTime: '08:30:00', expiresAt: '2026-08-10T01:05:00Z',
    });
    expect(component['holdProfileId']()).toBe(profile1Id);

    component['back']();
    component['form'].controls.profileId.setValue(profile2Id);
    component['chooseDate'](date!);
    component['chooseSlot'](component['availableSlots']()[0]);
    component['continueToDetails']();
    const hold2 = http.expectOne('/api/v1/appointment-holds');
    expect(hold2.request.body.profileId).toBe(profile2Id);
    hold2.flush({
      id: 'hold-2', specialty: 'Nội tổng quát', doctorName: 'Bác sĩ chuyên khoa',
      appointmentDate: date!.iso, startTime: '08:30:00', expiresAt: '2026-08-10T01:05:00Z',
    });

    expect(component['holdId']()).toBe('hold-2');
    expect(component['holdProfileId']()).toBe(profile2Id);
  });

  it('lets the patient filter available times by doctor and keeps that doctor visible in patient details', () => {
    component['chooseSpecialty']({ code: 'TQ', name: 'Khám Tổng Quát', description: 'Khám tổng quát' });
    const request = http.expectOne((item) => item.url === '/api/v1/appointment-slots');
    const date = component['dates']().find((item) => item.inCurrentMonth && item.iso >= component['today']);
    request.flush([
      {
        specialty: 'Khám Tổng Quát', appointmentDate: date!.iso, startTime: '08:00:00', endTime: '08:30:00',
        doctorName: 'Bác sĩ Nguyễn An', remainingCapacity: 1, doctorId: 'doctor-1', roomCode: 'TQ-01',
      },
      {
        specialty: 'Khám Tổng Quát', appointmentDate: date!.iso, startTime: '08:30:00', endTime: '09:00:00',
        doctorName: 'Bác sĩ Trần Minh', remainingCapacity: 1, doctorId: 'doctor-2', roomCode: 'TQ-02',
      },
    ]);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelectorAll('[data-testid="doctor-option"]')).toHaveLength(2);
    component['chooseDoctor']('doctor-2');
    component['chooseDate'](date!);

    expect(component['availableSlots']()).toHaveLength(1);
    expect(component['availableSlots']()[0].doctorName).toBe('Bác sĩ Trần Minh');
    component['chooseSlot'](component['availableSlots']()[0]);
    component['step'].set(3);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="selected-doctor"]')?.textContent)
      .toContain('Bác sĩ Trần Minh');
    expect(fixture.nativeElement.textContent).not.toContain('Phòng khám phân công theo chuyên khoa');
  });
});
