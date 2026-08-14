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

  it('adjusts a waiting queue ticket without editing its appointment status', () => {
    const component = fixture.componentInstance as any;
    component.query.set('CL-20260807-1234');
    component.search();
    http.expectOne((item) => item.url === '/api/v1/reception/appointments').flush([{
      ...appointment(), queueNumber: 5, queueStatus: 'WAITING', queueStatusLabel: 'Đang chờ', queueTicketId: 'ticket-1',
    }]);
    fixture.detectChanges();

    (fixture.nativeElement.querySelector('[data-testid="adjust-queue"]') as HTMLButtonElement).click();
    http.expectOne('/api/v1/reception/doctors').flush([
      { staffId: 'doctor-2', fullName: 'BS. Nguyễn Bình', specialty: 'Nhi khoa', roomCode: 'NHI-01', roomName: 'Phòng Nhi 01' },
    ]);
    component.adjustmentDoctorId.set('doctor-2');
    component.adjustmentReason.set('Điều chuyển theo phân công trong ca');
    component.adjustQueue('MOVE');
    const request = http.expectOne('/api/v1/queue/ticket-1/adjust');
    expect(request.request.body).toEqual({
      action: 'MOVE', targetDoctorId: 'doctor-2', targetRoomCode: 'NHI-01', targetSpecialty: 'Nhi khoa',
      reason: 'Điều chuyển theo phân công trong ca',
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
    expect(request.request.headers.get('Idempotency-Key')).toMatch(/^walk-in-/);
    expect(request.request.body).toEqual({
      phone: '0912345678', profileId: 'p-1', doctorId: 'd-1', appointmentDate: component.walkInDate(),
      startTime: '09:00:00', reason: 'Đau đầu từ sáng', exceptionReason: 'Người bệnh đến quầy không có lịch',
    });
    request.flush({ ...appointment(), queueNumber: 8, queueStatus: 'WAITING', queueStatusLabel: 'Đang chờ' });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Đã tạo lịch và cấp số 08');
  });

  it('starts OTP registration when the phone has no account', () => {
    const component = fixture.componentInstance as any;
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
    component.activationOtp.set('123456');
    component.activationPassword.set('new-password');
    component.activationConfirmPassword.set('new-password');
    component.activatePendingAccount();
    const activationRequest = http.expectOne('/api/v1/auth/activate');
    expect(activationRequest.request.body).toEqual({
      phone: '0912345678', otpCode: '123456', newPassword: 'new-password', confirmPassword: 'new-password',
    });
    activationRequest.flush(null);
    const profilesAfterActivation = http.expectOne('/api/v1/reception/profiles?phone=0912345678');
    profilesAfterActivation.flush([]);
  });

  it('creates a temporary profile when the phone cannot be verified', () => {
    const component = fixture.componentInstance as any;
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
    expect(input.placeholder).toBe('Ví dụ: Người bệnh đến quầy chưa đặt lịch');
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
