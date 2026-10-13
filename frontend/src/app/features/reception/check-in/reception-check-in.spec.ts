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
    http.expectOne('/api/v1/reception/worklist?date=' + (fixture.componentInstance as any).selectedDate()).flush([]);
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

  it('shows all appointments in the workday overview and filters them without changing totals', () => {
    const component = fixture.componentInstance as any;
    const worklist = Array.from({ length: 7 }, (_, index) => ({
      ...appointment(),
      id: `workday-${index + 1}`,
      appointmentCode: `CL-WORKDAY-${index + 1}`,
      patientName: `Người bệnh ${index + 1}`,
      patientPhone: `09123456${String(index + 10).slice(-2)}`,
    }));
    component.appointments.set(worklist);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelectorAll('[data-testid="overview-appointment"]').length).toBe(7);
    expect(component.dashboardCounts().total).toBe(7);

    const search = fixture.nativeElement.querySelector('[data-testid="overview-search"]') as HTMLInputElement;
    expect(search).not.toBeNull();
    search.value = 'nguoi benh 7';
    search.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelectorAll('[data-testid="overview-appointment"]').length).toBe(1);
    expect(fixture.nativeElement.textContent).toContain('Người bệnh 7');
    expect(fixture.nativeElement.textContent).not.toContain('Người bệnh 6');
    expect(component.dashboardCounts().total).toBe(7);
  });

  it('counts only active queue states as received and includes unperformed appointments in exceptions', () => {
    const component = fixture.componentInstance as any;
    component.appointments.set([
      { ...appointment(), id: 'booked', status: 'BOOKED', queueStatus: null },
      { ...appointment(), id: 'waiting', status: 'CHECKED_IN', queueStatus: 'WAITING' },
      { ...appointment(), id: 'in-service', status: 'CHECKED_IN', queueStatus: 'IN_SERVICE' },
      { ...appointment(), id: 'completed', status: 'COMPLETED', queueStatus: 'COMPLETED' },
      { ...appointment(), id: 'not-performed', status: 'NOT_PERFORMED', queueStatus: 'SKIPPED' },
      { ...appointment(), id: 'cancelled', status: 'CANCELLED', queueStatus: null },
    ]);
    component.activeTab.set('exceptions');

    expect(component.dashboardCounts()).toEqual({ total: 6, waiting: 1, checkedIn: 2, exceptions: 1 });
    expect(component.visibleAppointments().map((item: any) => item.id)).toContain('not-performed');
  });

  it('keeps the newest appointment search result when requests finish out of order', () => {
    const component = fixture.componentInstance as any;
    component.query.set('0911111111');
    component.search();
    const firstRequest = http.expectOne((item) => item.url === '/api/v1/reception/appointments'
      && item.params.get('query') === '0911111111');

    component.query.set('0922222222');
    component.search();
    const latestRequest = http.expectOne((item) => item.url === '/api/v1/reception/appointments'
      && item.params.get('query') === '0922222222');
    latestRequest.flush([{ ...appointment(), id: 'latest-search', patientPhone: '0922222222' }]);
    firstRequest.flush([{ ...appointment(), id: 'old-search', patientPhone: '0911111111' }]);

    expect(component.appointments().map((item: any) => item.id)).toEqual(['latest-search']);
  });

  it('keeps background refresh bound to the last submitted query', () => {
    const component = fixture.componentInstance as any;
    component.query.set('0911111111');
    component.search();
    http.expectOne((item) => item.url === '/api/v1/reception/appointments'
      && item.params.get('query') === '0911111111').flush([appointment()]);

    component.query.set('0922222222');
    component.refreshSearch();
    const refresh = http.expectOne((item) => item.url === '/api/v1/reception/appointments');

    expect(refresh.request.params.get('query')).toBe('0911111111');
    refresh.flush([appointment()]);
  });

  it('does not allow search results from a previous screen to replace the active queue', () => {
    const component = fixture.componentInstance as any;
    component.query.set('0911111111');
    component.search();
    const searchRequest = http.expectOne((item) => item.url === '/api/v1/reception/appointments');

    component.selectTab('profiles');
    component.selectTab('queue');
    const queueRequest = http.expectOne((item) => item.url === '/api/v1/reception/worklist');
    queueRequest.flush([{ ...appointment(), id: 'queue-result', status: 'CHECKED_IN', queueNumber: 2 }]);
    searchRequest.flush([{ ...appointment(), id: 'stale-search-result' }]);

    expect(component.appointments().map((item: any) => item.id)).toEqual(['queue-result']);
  });

  it('clears search results instead of showing them as queue data', () => {
    const component = fixture.componentInstance as any;
    component.query.set('0911111111');
    component.search();
    http.expectOne((item) => item.url === '/api/v1/reception/appointments').flush([
      { ...appointment(), id: 'search-result' },
    ]);

    component.selectTab('queue');
    expect(component.appointments()).toEqual([]);
    const queueRequest = http.expectOne((item) => item.url === '/api/v1/reception/worklist');
    queueRequest.flush([{ ...appointment(), id: 'queue-result', status: 'CHECKED_IN', queueNumber: 2 }]);
    expect(component.appointments().map((item: any) => item.id)).toEqual(['queue-result']);
  });

  it('renders one reception work screen without an additional in-page tab bar', () => {
    const component = fixture.componentInstance as any;

    expect(component.activeTab()).toBe('overview');
    expect(fixture.nativeElement.querySelector('[data-testid^="reception-tab-"]')).toBeNull();
    component.selectTab('queue');
    fixture.detectChanges();
    expect(component.activeTab()).toBe('queue');
    expect(fixture.nativeElement.querySelector('[data-testid="reception-queue-screen"]')).not.toBeNull();
  });

  it('loads the daily worklist independently for the queue screen', () => {
    const component = fixture.componentInstance as any;
    component.selectTab('queue');
    component.loadWorkspace();

    const request = http.expectOne('/api/v1/reception/worklist?date=' + component.selectedDate());
    request.flush([{ ...appointment(), status: 'CHECKED_IN', queueNumber: 5, queueStatus: 'WAITING', queueStatusLabel: 'Đang chờ' }]);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="reception-queue-screen"]').textContent).toContain('Nguyễn Thanh Vũ');
  });

  it('does not let a stale daily worklist replace a newer date selection', () => {
    const component = fixture.componentInstance as any;
    component.selectTab('queue');
    component.selectedDate.set('2026-08-07');
    component.loadWorkspace();
    const oldDateRequest = http.expectOne('/api/v1/reception/worklist?date=2026-08-07');

    component.onSelectedDateChange('2026-08-08');
    const currentDateRequest = http.expectOne('/api/v1/reception/worklist?date=2026-08-08');
    currentDateRequest.flush([{ ...appointment(), id: 'new-date', patientName: 'Nguyễn Thị An' }]);
    oldDateRequest.flush([{ ...appointment(), id: 'old-date', patientName: 'Nguyễn Thanh Vũ' }]);

    expect(component.appointments().map((item: any) => item.id)).toEqual(['new-date']);
  });

  it('searches patient profiles from the dedicated reception screen', () => {
    const component = fixture.componentInstance as any;
    component.selectTab('profiles');
    component.profileQuery.set('0912345678');
    component.searchProfiles();

    const request = http.expectOne('/api/v1/reception/profiles?phone=0912345678');
    request.flush([{ id: 'p-1', fullName: 'Nguyễn Thanh Vũ', relationship: 'Bản thân', phone: '0912345678', primaryProfile: true }]);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Nguyễn Thanh Vũ');
    expect(fixture.nativeElement.querySelector('[data-testid="open-profile-intake"]')).not.toBeNull();
  });

  it('keeps profile search results for the latest submitted phone number', () => {
    const component = fixture.componentInstance as any;
    component.profileQuery.set('0912345678');
    component.searchProfiles();
    const oldRequest = http.expectOne('/api/v1/reception/profiles?phone=0912345678');

    component.profileQuery.set('0987654321');
    component.searchProfiles();
    const latestRequest = http.expectOne('/api/v1/reception/profiles?phone=0987654321');
    latestRequest.flush([{ id: 'p-2', fullName: 'Nguyễn Thị An', relationship: 'Bản thân', phone: '0987654321', primaryProfile: true }]);
    oldRequest.flush([{ id: 'p-1', fullName: 'Nguyễn Thanh Vũ', relationship: 'Bản thân', phone: '0912345678', primaryProfile: true }]);

    expect(component.profileResults().map((profile: any) => profile.id)).toEqual(['p-2']);
    expect(component.profileLoading()).toBe(false);
  });

  it('ignores a walk-in profile response after the phone number changes', () => {
    const component = fixture.componentInstance as any;
    component.walkInPhone.set('0912345678');
    component.loadWalkInProfiles();
    const oldPhoneRequest = http.expectOne('/api/v1/reception/profiles?phone=0912345678');

    component.onWalkInPhoneChange('0987654321');
    component.loadWalkInProfiles();
    const currentPhoneRequest = http.expectOne('/api/v1/reception/profiles?phone=0987654321');
    currentPhoneRequest.flush([{ id: 'p-2', fullName: 'Nguyễn Thị An', relationship: 'Bản thân', phone: '0987654321', primaryProfile: true }]);
    oldPhoneRequest.flush([{ id: 'p-1', fullName: 'Nguyễn Thanh Vũ', relationship: 'Bản thân', phone: '0912345678', primaryProfile: true }]);

    expect(component.walkInProfiles().map((profile: any) => profile.id)).toEqual(['p-2']);
    expect(component.walkInProfileId()).toBe('p-2');
  });

  it('does not mark OTP as sent for a new phone when an older OTP request completes', () => {
    const component = fixture.componentInstance as any;
    component.walkInPhone.set('0912345678');
    component.requestRegistrationOtp();
    const oldPhoneRequest = http.expectOne('/api/v1/reception/patients/request-otp');

    component.onWalkInPhoneChange('0987654321');
    component.requestRegistrationOtp();
    const currentPhoneRequest = http.expectOne('/api/v1/reception/patients/request-otp');
    oldPhoneRequest.flush({ expiresInSeconds: 300, retryAfterSeconds: 60 });

    expect(component.registrationOtpSent()).toBe(false);
    currentPhoneRequest.flush({ expiresInSeconds: 300, retryAfterSeconds: 60 });
    expect(component.registrationOtpSent()).toBe(true);
  });

  it('keeps the walk-in dialog scrollable and removes redundant operational copy', () => {
    const component = fixture.componentInstance as any;
    component.walkInOpen.set(true);
    component.walkInProfileNeedsCompletion.set(true);
    fixture.detectChanges();

    const dialog = fixture.nativeElement.querySelector('[data-testid="walk-in-dialog"]') as HTMLElement;
    const body = fixture.nativeElement.querySelector('[data-testid="walk-in-scroll-body"]') as HTMLElement;
    expect(dialog.classList.contains('flex')).toBe(true);
    expect(body.classList.contains('overflow-y-auto')).toBe(true);
    expect(dialog.textContent).not.toContain('Chỉ áp dụng cho ngày hôm nay');
  });

  it('does not leave a stale global notice after completing a profile inside the intake flow', () => {
    const component = fixture.componentInstance as any;
    component.walkInProfileId.set('p-1');
    component.registrationFullName.set('Nguyễn Thanh Vũ');
    component.registrationDateOfBirth.set('2005-06-07');
    component.registrationGender.set('Nam');
    component.submitProfileCompletion();

    http.expectOne('/api/v1/patient-profiles/p-1/reception-update').flush({
      id: 'p-1', fullName: 'Nguyễn Thanh Vũ', relationship: 'Bản thân', primaryProfile: true, accountStatus: 'ACTIVE',
    });
    expect(component.notice()).toBe('');
  });

  it('does not copy province district and ward names into the street field', () => {
    const component = fixture.componentInstance as any;
    component.registrationProvinces.set([{ code: '72', name: 'Tây Ninh' }]);

    component.prepareProfileCompletion({
      id: 'p-1', fullName: 'Nguyễn Thanh Vũ', relationship: 'Bản thân', primaryProfile: true, accountStatus: 'ACTIVE',
      provinceName: 'Tây Ninh', districtName: 'Trảng Bàng', wardName: 'An Tịnh',
      streetAddress: 'An Tịnh, Trảng Bàng, Tây Ninh',
    });

    expect(component.registrationStreetAddress()).toBe('');
  });

  it('keeps districts for the latest selected province when address requests finish out of order', () => {
    const component = fixture.componentInstance as any;
    component.registrationProvinces.set([
      { code: '01', name: 'Hà Nội' },
      { code: '79', name: 'TP. Hồ Chí Minh' },
    ]);

    component.registrationProvinceCode.set('01');
    component.registrationProvinceChanged();
    const oldDistricts = http.expectOne('/api/v1/addresses/provinces/01/districts?page=1&limit=100');

    component.registrationProvinceCode.set('79');
    component.registrationProvinceChanged();
    const latestDistricts = http.expectOne('/api/v1/addresses/provinces/79/districts?page=1&limit=100');
    latestDistricts.flush([{ code: '760', name: 'Quận 1' }]);
    oldDistricts.flush([{ code: '001', name: 'Ba Đình' }]);

    expect(component.registrationDistricts().map((item: any) => item.name)).toEqual(['Quận 1']);
    expect(component.registrationAddressLoading()).toBe(false);
  });

  it('keeps wards for the latest selected district when address requests finish out of order', () => {
    const component = fixture.componentInstance as any;
    component.registrationDistricts.set([
      { code: '001', name: 'Ba Đình' },
      { code: '760', name: 'Quận 1' },
    ]);

    component.registrationDistrictCode.set('001');
    component.registrationDistrictChanged();
    const oldWards = http.expectOne('/api/v1/addresses/districts/001/wards?page=1&limit=100');

    component.registrationDistrictCode.set('760');
    component.registrationDistrictChanged();
    const latestWards = http.expectOne('/api/v1/addresses/districts/760/wards?page=1&limit=100');
    latestWards.flush([{ code: '26734', name: 'Bến Nghé' }]);
    oldWards.flush([{ code: '00001', name: 'Phúc Xá' }]);

    expect(component.registrationWards().map((item: any) => item.name)).toEqual(['Bến Nghé']);
    expect(component.registrationAddressLoading()).toBe(false);
  });

  it('only requires the three minimum identity fields before reception', () => {
    const component = fixture.componentInstance as any;
    component.prepareProfileCompletion({
      id: 'p-1', fullName: 'Nguyễn Thanh Vũ', relationship: 'Bản thân', primaryProfile: true,
      accountStatus: 'ACTIVE', dateOfBirth: '2005-06-07', gender: 'Nam', identityNumber: undefined,
      nationality: undefined, ethnicity: undefined, provinceCode: undefined,
    });

    expect(component.walkInProfileNeedsCompletion()).toBe(false);
  });

  it('does not send duplicate check-in requests while the first request is pending', () => {
    const component = fixture.componentInstance as any;
    component.exceptionReason.set('QR phòng bị lỗi');
    component.checkIn(appointment());
    component.checkIn(appointment());

    const requests = http.match('/api/v1/reception/appointments/a-1/check-in');
    expect(requests).toHaveLength(1);
    requests[0].flush({ ...appointment(), queueNumber: 5, queueStatus: 'WAITING', queueStatusLabel: 'Đang chờ' });
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

  it('records a checked-in patient leaving before examination', () => {
    const component = fixture.componentInstance as any;
    component.query.set('CL-20260807-1234');
    component.search();
    http.expectOne((item) => item.url === '/api/v1/reception/appointments').flush([
      { ...appointment(), status: 'CHECKED_IN', queueNumber: 5, queueStatus: 'WAITING', queueStatusLabel: 'Đang chờ' },
    ]);
    fixture.detectChanges();
    component.leaveReason.set('Người bệnh bận việc đột xuất');
    fixture.detectChanges();

    (fixture.nativeElement.querySelector('[data-testid="leave-before-exam"]') as HTMLButtonElement).click();
    const request = http.expectOne('/api/v1/reception/appointments/a-1/leave');
    expect(request.request.body).toEqual({ reason: 'Người bệnh bận việc đột xuất' });
    request.flush({ ...appointment(), status: 'NOT_PERFORMED', queueNumber: 5, queueStatus: 'LEFT_BEFORE_EXAM', queueStatusLabel: 'Rời trước khám' });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Rời trước khám');
  });

  it('keeps each reception action locked until its own request finishes', () => {
    const component = fixture.componentInstance as any;
    const first = { ...appointment(), status: 'CHECKED_IN', queueStatus: 'WAITING' };
    const second = { ...appointment(), id: 'a-2', status: 'CHECKED_IN', queueStatus: 'WAITING' };
    component.leaveReason.set('NgÆ°á»i bá»‡nh báº­n viá»‡c Ä‘á»™t xuáº¥t');

    component.leaveBeforeExam(first);
    component.leaveBeforeExam(second);
    const firstRequest = http.expectOne('/api/v1/reception/appointments/a-1/leave');
    const secondRequest = http.expectOne('/api/v1/reception/appointments/a-2/leave');
    expect(component.isAppointmentBusy('a-1')).toBe(true);
    expect(component.isAppointmentBusy('a-2')).toBe(true);

    firstRequest.flush({ ...first, status: 'NOT_PERFORMED', queueStatus: 'LEFT_BEFORE_EXAM' });
    expect(component.isAppointmentBusy('a-1')).toBe(false);
    expect(component.isAppointmentBusy('a-2')).toBe(true);

    component.leaveBeforeExam(second);
    expect(http.match('/api/v1/reception/appointments/a-2/leave')).toHaveLength(0);
    secondRequest.flush({ ...second, status: 'NOT_PERFORMED', queueStatus: 'LEFT_BEFORE_EXAM' });
    expect(component.isAppointmentBusy('a-2')).toBe(false);
  });

  it('records a facility outage for an existing queue ticket', () => {
    const component = fixture.componentInstance as any;
    const checkedIn = { ...appointment(), status: 'CHECKED_IN', queueNumber: 5,
      queueStatus: 'WAITING', queueStatusLabel: 'Đang chờ', queueTicketId: 'ticket-1' };
    component.appointments.set([checkedIn]);
    component.exceptionReason.set('Phòng tạm dừng do thiết bị hỏng');

    component.markFacilityUnavailable(checkedIn);

    const request = http.expectOne('/api/v1/reception/appointments/a-1/facility-unavailable');
    expect(request.request.body).toEqual({ reason: 'Phòng tạm dừng do thiết bị hỏng' });
    request.flush({ ...checkedIn, status: 'NOT_PERFORMED', queueStatus: 'COMPLETED', queueStatusLabel: 'Đã hoàn tất' });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('phòng khám tạm dừng phục vụ');
  });

  it('adjusts a waiting queue ticket without editing its appointment status', () => {
    const component = fixture.componentInstance as any;
    component.query.set('CL-20260807-1234');
    component.search();
    http.expectOne((item) => item.url === '/api/v1/reception/appointments').flush([{
      ...appointment(), queueNumber: 5, queueStatus: 'WAITING', queueStatusLabel: 'Đang chờ', queueTicketId: 'ticket-1',
    }]);
    fixture.detectChanges();

    (fixture.nativeElement.querySelector('[data-testid="adjust-queue"]') as HTMLButtonElement).click();
    http.expectOne((r) => r.url.includes('/api/v1/reception/doctors')).flush([
      { staffId: 'doctor-2', fullName: 'BS. Nguyễn Bình', specialty: 'Nhi khoa', roomCode: 'NHI-01', roomName: 'Phòng Nhi 01', shiftStatus: 'ACTIVE', waitingCount: 0, slots: [{ startTime: '09:00:00', endTime: '09:30:00', remaining: 1 }] },
    ]);
    component.adjustmentDoctorId.set('doctor-2');
    component.adjustmentSelectedSlot.set('09:00');
    component.adjustmentReason.set('Điều chuyển theo phân công trong ca');
    component.adjustQueue('MOVE');
    const request = http.expectOne('/api/v1/queue/ticket-1/adjust');
    expect(request.request.body).toEqual({
      action: 'MOVE', targetDoctorId: 'doctor-2', targetRoomCode: 'NHI-01', targetSpecialty: 'Nhi khoa',
      reason: 'Điều chuyển theo phân công trong ca', targetStartTime: '09:00',
    });
    request.flush({
      id: 'ticket-1', queueNumber: 8, roomCode: 'NHI-01', roomName: 'Phòng Nhi 01', queueDate: '2026-08-07',
      appointmentTime: '09:00:00', status: 'WAITING', statusLabel: 'Đang chờ', appointmentCode: 'CL-20260807-1234',
      specialty: 'Nhi khoa', doctorName: 'BS. Nguyễn Bình', priority: false,
    });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Nhi khoa');
    expect(fixture.nativeElement.textContent).toContain('BS. Nguyễn Bình');
  });

  it('does not submit queue adjustment twice while the first update is pending', () => {
    const component = fixture.componentInstance as any;
    component.adjustmentTicket.set({ ...appointment(), queueTicketId: 'ticket-1', queueStatus: 'WAITING' });
    component.adjustmentReason.set('Æ¯u tiÃªn ngÆ°á»i bá»‡nh cáº¥p cá»©u');

    component.adjustQueue('SET_PRIORITY');
    component.adjustQueue('SET_PRIORITY');

    const requests = http.match('/api/v1/queue/ticket-1/adjust');
    requests.forEach((request) => request.flush({
      id: 'ticket-1', queueNumber: 5, roomCode: 'NOI-01', roomName: 'PhÃ²ng Ná»™i 01',
      queueDate: '2026-08-07', appointmentTime: '09:00:00', status: 'WAITING', statusLabel: 'Äang chá»',
      appointmentCode: 'CL-20260807-1234', specialty: 'Ná»™i tá»•ng quÃ¡t', doctorName: 'BS. Nguyá»…n An', priority: true,
    }));
    expect(requests).toHaveLength(1);
  });

  it('does not let an older queue-adjustment doctor list replace the selected ticket availability', () => {
    const component = fixture.componentInstance as any;
    const firstTicket = { ...appointment(), queueStatus: 'WAITING', queueTicketId: 'ticket-1' };
    const selectedTicket = { ...appointment(), id: 'a-2', queueStatus: 'WAITING', queueTicketId: 'ticket-2' };

    component.openAdjustment(firstTicket);
    const firstAvailability = http.expectOne((request) => request.url === '/api/v1/reception/doctors');
    component.openAdjustment(selectedTicket);
    const selectedAvailability = http.expectOne((request) => request.url === '/api/v1/reception/doctors');

    selectedAvailability.flush([{
      staffId: 'doctor-2', fullName: 'BS. Äang chá»n', specialty: 'Nhi khoa', roomCode: 'NHI-01',
      shiftStatus: 'ACTIVE', waitingCount: 0, slots: [],
    }]);
    firstAvailability.flush([{
      staffId: 'doctor-1', fullName: 'BS. Cá»§', specialty: 'Ná»™i khoa', roomCode: 'NOI-01',
      shiftStatus: 'ACTIVE', waitingCount: 0, slots: [],
    }]);

    expect(component.adjustmentTicket().queueTicketId).toBe('ticket-2');
    expect(component.doctors().map((doctor: any) => doctor.staffId)).toEqual(['doctor-2']);
  });

  it('opens the walk-in form and creates an appointment from a selected slot', () => {
    const component = fixture.componentInstance as any;
    component.activeTab.set('intake');
    fixture.detectChanges();
    const openButton = fixture.nativeElement.querySelector('[data-testid="open-walk-in"]') as HTMLButtonElement;
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
    expect(request.request.headers.get('Idempotency-Key')).toMatch(/^walk-in-/);
    expect(request.request.body).toEqual({
      phone: '0912345678', profileId: 'p-1', doctorId: 'd-1', appointmentDate: component.walkInDate(),
      startTime: '09:00:00', reason: 'Đau đầu từ sáng', exceptionReason: 'Người bệnh đến quầy không có lịch',
    });
    request.flush({ ...appointment(), queueNumber: 8, queueStatus: 'WAITING', queueStatusLabel: 'Đang chờ' });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Đã tạo lịch và cấp số 08');
  });

  it('uses a new idempotency key when retrying after the walk-in payload changes', () => {
    const component = fixture.componentInstance as any;
    component.walkInPhone.set('0912345678');
    component.walkInSlots.set([
      { specialty: 'Nội tổng quát', appointmentDate: component.walkInDate(), startTime: '09:00:00', endTime: '09:30:00', doctorName: 'BS. Nguyễn An', remainingCapacity: 1, doctorId: 'd-1', roomCode: 'NOI-01' },
      { specialty: 'Nội tổng quát', appointmentDate: component.walkInDate(), startTime: '09:30:00', endTime: '10:00:00', doctorName: 'BS. Nguyễn An', remainingCapacity: 1, doctorId: 'd-1', roomCode: 'NOI-01' },
    ]);
    component.walkInStartTime.set('09:00:00');
    component.walkInReason.set('Đau đầu từ sáng');
    component.walkInExceptionReason.set('Người bệnh đến quầy không có lịch');

    component.submitWalkIn();
    const firstRequest = http.expectOne('/api/v1/reception/walk-in');
    const firstKey = firstRequest.request.headers.get('Idempotency-Key');
    firstRequest.flush({ code: 'SLOT_UNAVAILABLE', message: 'Khung giờ vừa được sử dụng.' },
      { status: 409, statusText: 'Conflict' });

    component.walkInStartTime.set('09:30:00');
    component.submitWalkIn();
    const retry = http.expectOne('/api/v1/reception/walk-in');

    expect(retry.request.headers.get('Idempotency-Key')).not.toBe(firstKey);
    retry.flush(appointment());
  });

  it('reuses the idempotency key when retrying the same walk-in payload', () => {
    const component = fixture.componentInstance as any;
    component.walkInPhone.set('0912345678');
    component.walkInSlots.set([
      { specialty: 'Nội tổng quát', appointmentDate: component.walkInDate(), startTime: '09:00:00', endTime: '09:30:00', doctorName: 'BS. Nguyễn An', remainingCapacity: 1, doctorId: 'd-1', roomCode: 'NOI-01' },
    ]);
    component.walkInStartTime.set('09:00:00');
    component.walkInReason.set('Đau đầu từ sáng');
    component.walkInExceptionReason.set('Người bệnh đến quầy không có lịch');

    component.submitWalkIn();
    const firstRequest = http.expectOne('/api/v1/reception/walk-in');
    const firstKey = firstRequest.request.headers.get('Idempotency-Key');
    firstRequest.flush({ code: 'NETWORK_FAILURE', message: 'Mạng không ổn định.' },
      { status: 503, statusText: 'Service Unavailable' });

    component.submitWalkIn();
    const retry = http.expectOne('/api/v1/reception/walk-in');

    expect(retry.request.headers.get('Idempotency-Key')).toBe(firstKey);
    retry.flush(appointment());
  });

  it('keeps a zero-capacity slot available for an authorized overflow reception', () => {
    const component = fixture.componentInstance as any;
    component.walkInSpecialty.set('Nội tổng quát');
    component.loadWalkInSlots();
    http.expectOne((item) => item.url === '/api/v1/appointment-slots').flush([
      { specialty: 'Nội tổng quát', appointmentDate: component.walkInDate(), startTime: '10:00:00', endTime: '10:30:00', doctorName: 'BS. Nguyễn An', remainingCapacity: 0, doctorId: 'd-1', roomCode: 'NOI-01' },
    ]);

    expect(component.walkInSlots()).toHaveLength(1);
    expect(component.walkInSlots()[0].remainingCapacity).toBe(0);
  });

  it('does not let an older slot search replace the selected specialty', () => {
    const component = fixture.componentInstance as any;
    component.walkInSpecialty.set('Nội tổng quát');
    component.loadWalkInSlots();
    const oldSearch = http.expectOne((item) => item.url === '/api/v1/appointment-slots');

    component.walkInSpecialty.set('Nhi khoa');
    component.loadWalkInSlots();
    const currentSearch = http.expectOne((item) => item.url === '/api/v1/appointment-slots');
    currentSearch.flush([{
      specialty: 'Nhi khoa', appointmentDate: component.walkInDate(), startTime: '10:00:00', endTime: '10:30:00',
      doctorName: 'BS. Nguyễn Bình', remainingCapacity: 2, doctorId: 'doctor-pediatrics', roomCode: 'NHI-01',
    }]);
    oldSearch.flush([{
      specialty: 'Nội tổng quát', appointmentDate: component.walkInDate(), startTime: '09:00:00', endTime: '09:30:00',
      doctorName: 'BS. Nguyễn An', remainingCapacity: 3, doctorId: 'doctor-general', roomCode: 'NOI-01',
    }]);

    expect(component.walkInSlots().map((slot: any) => slot.doctorId)).toEqual(['doctor-pediatrics']);
    expect(component.walkInStartTime()).toBe('10:00:00');
  });

  it('confirms that a called patient has returned without issuing a second number', () => {
    const component = fixture.componentInstance as any;
    component.selectTab('exceptions');
    component.appointments.set([{ ...appointment(), status: 'CHECKED_IN', queueNumber: 5,
      queueStatus: 'WAITING', queueStatusLabel: 'Đang chờ', queuePresenceStatus: 'RETURN_REQUIRED' }]);
    fixture.detectChanges();

    (fixture.nativeElement.querySelector('[data-testid="mark-returned"]') as HTMLButtonElement).click();
    const request = http.expectOne('/api/v1/reception/appointments/a-1/check-in');
    expect(request.request.body.reason).toContain('quay lại');
    request.flush({ ...appointment(), status: 'CHECKED_IN', queueNumber: 5,
      queueStatus: 'WAITING', queueStatusLabel: 'Đang chờ', queuePresenceStatus: 'READY' });

    expect(component.appointments()[0].queueNumber).toBe(5);
    expect(component.appointments()[0].queuePresenceStatus).toBe('READY');
  });

  it('starts OTP registration when the phone has no account', () => {
    const component = fixture.componentInstance as any;
    component.activeTab.set('intake');
    fixture.detectChanges();
    const openButton = fixture.nativeElement.querySelector('[data-testid="open-walk-in"]') as HTMLButtonElement;
    openButton.click();
    http.expectOne((item) => item.url === '/api/v1/specialties').flush([]);
    component.walkInPhone.set('0912345678');
    component.loadWalkInProfiles();
    http.expectOne((item) => item.url === '/api/v1/reception/profiles').flush({ error: { message: 'Chưa tìm thấy tài khoản' } }, { status: 404, statusText: 'Not Found' });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Chưa có tài khoản');
    component.requestRegistrationOtp();
    const otpRequest = http.expectOne('/api/v1/reception/patients/request-otp');
    expect(otpRequest.request.body).toEqual({ phone: '0912345678' });
    otpRequest.flush({ expiresInSeconds: 300, retryAfterSeconds: 60 });
    component.walkInOtp.set('123456');
    component.registrationFullName.set('Nguyễn An');
    component.registrationDateOfBirth.set('1995-06-07');
    component.registrationGender.set('Nữ');
    component.submitRegistration();
    const registrationRequest = http.expectOne('/api/v1/reception/patients');
    expect(registrationRequest.request.body.fullName).toBe('Nguyễn An');
    registrationRequest.flush({ accountId: 'account-1', phone: '0912345678', fullName: 'Nguyễn An', mustChangePassword: true });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Đã tạo tài khoản cho Nguyễn An');
    component.activationPassword.set('new-password');
    component.activationConfirmPassword.set('new-password');
    component.activatePendingAccount();
    const activationRequest = http.expectOne('/api/v1/auth/activate');
    expect(activationRequest.request.body).toEqual({
      phone: '0912345678', otpCode: null, newPassword: 'new-password', confirmPassword: 'new-password',
    });
    activationRequest.flush(null);
    const profilesAfterActivation = http.expectOne('/api/v1/reception/profiles?phone=0912345678');
    profilesAfterActivation.flush([]);
  });

  it('does not request a second registration OTP while the first request is pending', () => {
    const component = fixture.componentInstance as any;
    component.walkInPhone.set('0912345678');

    component.requestRegistrationOtp();
    component.requestRegistrationOtp();

    const requests = http.match('/api/v1/reception/patients/request-otp');
    expect(requests).toHaveLength(1);
    requests[0].flush({ expiresInSeconds: 300, retryAfterSeconds: 60 });
  });

  it('lets an existing pending account request and submit a fresh activation OTP', () => {
    const component = fixture.componentInstance as any;
    component.activeTab.set('intake');
    fixture.detectChanges();
    (fixture.nativeElement.querySelector('[data-testid="open-walk-in"]') as HTMLButtonElement).click();
    http.expectOne((item) => item.url === '/api/v1/specialties').flush([]);
    component.walkInPhone.set('0912345678');
    component.loadWalkInProfiles();
    http.expectOne('/api/v1/reception/profiles?phone=0912345678').flush([{
      id: 'p-pending', fullName: 'Nguyễn An', phone: '0912345678', primaryProfile: true,
      accountStatus: 'ACTIVE', mustChangePassword: true,
    }]);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="pending-account-otp"]')).not.toBeNull();
    component.activationPassword.set('new-password');
    component.activationConfirmPassword.set('new-password');
    component.activatePendingAccount();
    http.expectNone('/api/v1/auth/activate');
    expect(component.error()).toContain('xác minh OTP');

    component.requestPendingActivationOtp();
    http.expectOne('/api/v1/reception/patients/request-otp').flush({ expiresInSeconds: 300, retryAfterSeconds: 60 });
    fixture.detectChanges();

    const otpInput = fixture.nativeElement.querySelector('[data-testid="pending-account-otp-input"]') as HTMLInputElement;
    expect(otpInput).not.toBeNull();
    component.walkInOtp.set('123456');
    component.verifyPendingActivationOtp();
    const verificationRequest = http.expectOne('/api/v1/auth/verify-sms-otp');
    expect(verificationRequest.request.body).toEqual({ phone: '0912345678', purpose: 'REGISTRATION', code: '123456' });
    verificationRequest.flush({ verified: true });
    component.activatePendingAccount();

    const activationRequest = http.expectOne('/api/v1/auth/activate');
    expect(activationRequest.request.body).toEqual({
      phone: '0912345678', otpCode: null, newPassword: 'new-password', confirmPassword: 'new-password',
    });
    activationRequest.flush(null);
    http.expectOne('/api/v1/reception/profiles?phone=0912345678').flush([]);
  });

  it('blocks reception for a locked account instead of showing an unusable activation form', () => {
    const component = fixture.componentInstance as any;
    component.activeTab.set('intake');
    fixture.detectChanges();
    (fixture.nativeElement.querySelector('[data-testid="open-walk-in"]') as HTMLButtonElement).click();
    http.expectOne((item) => item.url === '/api/v1/specialties').flush([]);
    component.walkInPhone.set('0912345678');
    component.loadWalkInProfiles();
    http.expectOne('/api/v1/reception/profiles?phone=0912345678').flush([{
      id: 'p-locked', fullName: 'Nguyễn An', phone: '0912345678', primaryProfile: true,
      accountStatus: 'LOCKED', mustChangePassword: false,
    }]);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="locked-account-message"]')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('[name="activationPassword"]')).toBeNull();
    expect(component.walkInNeedsPasswordChange()).toBe(false);
  });

  it('creates a temporary profile when the phone cannot be verified', () => {
    const component = fixture.componentInstance as any;
    component.activeTab.set('intake');
    fixture.detectChanges();
    (fixture.nativeElement.querySelector('[data-testid="open-walk-in"]') as HTMLButtonElement).click();
    http.expectOne((item) => item.url === '/api/v1/specialties').flush([]);
    component.walkInPhone.set('0912345678');
    component.loadWalkInProfiles();
    http.expectOne((item) => item.url === '/api/v1/reception/profiles').flush(
      { error: { message: 'Chưa tìm thấy tài khoản' } },
      { status: 404, statusText: 'Not Found' },
    );

    component.openTemporaryProfileForm();
    component.registrationFullName.set('Nguyễn Văn Tạm');
    component.registrationDateOfBirth.set('1990-01-01');
    component.registrationGender.set('Nam');
    component.submitRegistration();
    const request = http.expectOne('/api/v1/reception/temporary-profiles');
    expect(request.request.body).toEqual({
      phone: '0912345678', fullName: 'Nguyễn Văn Tạm', dateOfBirth: '1990-01-01', gender: 'Nam',
      identityNumber: undefined, nationality: 'Việt Nam', ethnicity: 'Kinh', address: undefined,
    });
    request.flush({ id: 'temp-1', fullName: 'Nguyễn Văn Tạm', relationship: 'Tạm tại quầy', primaryProfile: false });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Đã tạo hồ sơ tạm');
    expect(component.walkInProfileId()).toBe('temp-1');
  });

  it('requires a detailed reason when checking in a temporary profile', () => {
    const component = fixture.componentInstance as any;
    component.walkInPhone.set('0912345678');
    component.walkInProfiles.set([{
      id: 'temp-1', fullName: 'Nguyễn Văn Tạm', relationship: 'Tạm tại quầy',
      accountStatus: null, mustChangePassword: false,
    }]);
    component.walkInProfileId.set('temp-1');
    component.walkInSlots.set([{
      specialty: 'Nội tổng quát', appointmentDate: component.walkInDate(), startTime: '09:00:00',
      endTime: '09:30:00', doctorName: 'BS. Nguyễn An', doctorId: 'd-1', roomCode: 'NOI-01', remainingCapacity: 1,
    }]);
    component.walkInStartTime.set('09:00:00');
    component.walkInReason.set('Đau đầu từ sáng');
    component.walkInExceptionReason.set('Không rõ');

    component.submitWalkIn();

    expect(http.match('/api/v1/reception/walk-in')).toHaveLength(0);
    expect(component.error()).toContain('10 đến 500 ký tự');
  });

  it('shows the regular exception prompt for an existing patient profile', () => {
    const component = fixture.componentInstance as any;
    component.walkInOpen.set(true);
    component.walkInProfiles.set([{
      id: 'patient-1', fullName: 'Nguyễn Thanh Vũ', relationship: 'Bản thân',
      accountStatus: 'ACTIVE', mustChangePassword: false,
    }]);
    component.walkInProfileId.set('patient-1');
    fixture.detectChanges();

    const input = fixture.nativeElement.querySelector('input[name="walkInExceptionReason"]') as HTMLInputElement;
    expect(input.placeholder).toBe('Người bệnh đến quầy chưa đặt lịch');
  });

  it('filters doctors to same specialty by default', () => {
    const c = fixture.componentInstance as any;
    c.doctors.set([
      { staffId: 'd1', fullName: 'BS A', specialty: 'Noi tong quat', roomCode: 'NOI-01', shiftStatus: 'ACTIVE', waitingCount: 0, slots: [] },
      { staffId: 'd2', fullName: 'BS B', specialty: 'Nhi khoa', roomCode: 'NHI-01', shiftStatus: 'ACTIVE', waitingCount: 0, slots: [] },
    ]);
    c.adjustmentTicket.set({ specialty: 'Noi tong quat' });
    expect(c.filteredDoctors().length).toBe(1);
    expect(c.filteredDoctors()[0].staffId).toBe('d1');
    c.adjustmentShowDifferentSpecialty.set(true);
    expect(c.filteredDoctors().length).toBe(2);
  });

  it('blocks move when target doctor is outside shift', () => {
    const c = fixture.componentInstance as any;
    c.doctors.set([{ staffId: 'd1', fullName: 'BS A', specialty: 'Noi tong quat', roomCode: 'NOI-01', shiftStatus: 'NONE', waitingCount: 0, slots: [{ startTime: '09:00:00', endTime: '09:30:00', remaining: 1 }] }]);
    c.adjustmentTicket.set({ id: 'a-1', queueTicketId: 't-1', specialty: 'Noi tong quat' });
    c.adjustmentDoctorId.set('d1');
    c.adjustmentSelectedSlot.set('09:00');
    c.adjustmentReason.set('Ly do chuyen hop le 12345');
    c.adjustQueue('MOVE');
    expect(c.error()).toContain('ngoài ca');
    expect(http.match('/api/v1/queue/t-1/adjust').length).toBe(0);
  });

  it('late rebook hides full slots and the same slot', () => {
    const c = fixture.componentInstance as any;
    const appt = { ...appointment(), status: 'BOOKED', startTime: '09:00:00', specialty: 'Nội tổng quát', serviceId: 'service-123' };
    c.openRebook(appt);
    expect(c.rebookMode()).toBe('LATE');
    const req = http.expectOne((item) => item.url === '/api/v1/appointment-slots');
    expect(req.request.params.get('serviceId')).toBe('service-123');
    const today = c.selectedDate();
    req.flush([
      { appointmentDate: today, startTime: '09:00:00', endTime: '09:30:00', specialty: 'Nội tổng quát',
        doctorName: 'BS A', doctorId: 'd-1', remainingCapacity: 1 }, // same slot -> hidden
      { appointmentDate: today, startTime: '09:30:00', endTime: '10:00:00', specialty: 'Nội tổng quát',
        doctorName: 'BS A', doctorId: 'd-1', remainingCapacity: 0 }, // full -> hidden
      { appointmentDate: today, startTime: '10:00:00', endTime: '10:30:00', specialty: 'Nội tổng quát',
        doctorName: 'BS A', doctorId: 'd-1', remainingCapacity: 1 }, // shown
    ]);
    const slots = c.rebookSlots();
    expect(slots.length).toBe(1);
    expect(slots[0].startTime).toBe('10:00:00');
    expect(slots[0].remainingCapacity).toBe(1);
    expect(c.rebookStartTime()).toBe('10:00:00');
  });

  it('ignores rebook availability responses for an older selected date', () => {
    const c = fixture.componentInstance as any;
    const today = c.selectedDate();
    const firstDate = c.addDays(today, 1);
    const currentDate = c.addDays(today, 2);
    c.rebookTicket.set({ ...appointment(), status: 'ABSENT' });
    c.rebookMode.set('ABSENT');
    c.rebookDate.set(firstDate);
    c.loadRebookSlots();
    c.rebookDate.set(currentDate);
    c.loadRebookSlots();

    const requests = http.match((request) => request.url === '/api/v1/appointment-slots');
    expect(requests).toHaveLength(2);
    requests[1].flush([{
      appointmentDate: currentDate, startTime: '10:00:00', endTime: '10:30:00',
      specialty: 'Nội tổng quát', doctorName: 'BS A', doctorId: 'doctor-1', remainingCapacity: 1,
    }]);
    requests[0].flush([{
      appointmentDate: firstDate, startTime: '09:00:00', endTime: '09:30:00',
      specialty: 'Nội tổng quát', doctorName: 'BS A', doctorId: 'doctor-1', remainingCapacity: 1,
    }]);

    expect(c.rebookSlots().map((slot: any) => slot.appointmentDate)).toEqual([currentDate]);
    expect(c.rebookStartTime()).toBe('10:00:00');
  });

  it('limits absent-appointment rebooking to the original clinic service', () => {
    const c = fixture.componentInstance as any;
    const serviceId = 'service-123';
    c.rebookTicket.set({ ...appointment(), status: 'ABSENT', serviceId });
    c.rebookMode.set('ABSENT');
    c.rebookDate.set(c.addDays(c.selectedDate(), 1));
    c.loadRebookSlots();

    const request = http.expectOne((item) => item.url === '/api/v1/appointment-slots');
    expect(request.request.params.get('serviceId')).toBe(serviceId);
    request.flush([]);
  });

  it('does not submit duplicate absent rebooking requests while one is pending', () => {
    const c = fixture.componentInstance as any;
    const date = c.addDays(c.selectedDate(), 1);
    const currentAppointment = { ...appointment(), status: 'ABSENT' };
    c.rebookTicket.set(currentAppointment);
    c.rebookMode.set('ABSENT');
    c.rebookDate.set(date);
    c.rebookSlots.set([{
      appointmentDate: date, startTime: '10:00:00', endTime: '10:30:00',
      specialty: 'Nội tổng quát', doctorName: 'BS A', doctorId: 'doctor-1', remainingCapacity: 1,
    }]);
    c.rebookStartTime.set('10:00:00');
    c.rebookReason.set('Người bệnh xin đặt lại lịch khám');

    c.submitRebook();
    c.submitRebook();

    const requests = http.match((request) => request.url === '/api/v1/reception/appointments/a-1/rebook');
    expect(requests).toHaveLength(1);
    requests[0].flush({ ...currentAppointment, id: 'a-2', status: 'BOOKED', appointmentCode: 'CL-NEW-001' });
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
