import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable, tap } from 'rxjs';

export type OtpPurpose = 'LOGIN' | 'REGISTRATION' | 'RECOVERY';

export interface OtpResponse {
  expiresInSeconds: number;
  retryAfterSeconds: number;
}

export interface CheckPhoneResponse {
  accountExists: boolean;
}

export interface SmsLoginResponse {
  accessToken: string;
  tokenType: string;
  expiresAt: string;
  accountId: string;
  fullName: string;
  mustChangePassword: boolean;
}

export interface RegistrationResponse {
  accountId: string;
  phone: string;
  fullName: string;
}

export interface PatientProfileResponse {
  accountId: string;
  phone: string;
  fullName: string;
  dateOfBirth: string | null;
  gender: string | null;
  address: string | null;
  identityNumber: string | null;
  nationality: string | null;
  ethnicity: string | null;
  provinceCode: string | null;
  provinceName: string | null;
  districtCode: string | null;
  districtName: string | null;
  wardCode: string | null;
  wardName: string | null;
  streetAddress: string | null;
  status: string;
  mustChangePassword: boolean;
}

export interface PatientProfileItem {
  id: string;
  fullName: string;
  relationship: string;
  dateOfBirth: string;
  gender: string;
  phone: string | null;
  identityNumber: string | null;
  nationality: string | null;
  ethnicity: string | null;
  address: string | null;
  provinceCode: string | null;
  provinceName: string | null;
  districtCode: string | null;
  districtName: string | null;
  wardCode: string | null;
  wardName: string | null;
  streetAddress: string | null;
  primaryProfile: boolean;
}

export interface AppointmentResponse {
  id: string;
  appointmentCode: string;
  specialty: string;
  doctorName: string;
  appointmentDate: string;
  startTime: string;
  reason: string;
  status: string;
  statusLabel: string;
  profileId?: string | null;
  profileName?: string | null;
}

export interface ExaminationSessionResponse {
  id: string;
  appointmentId: string;
  appointmentCode: string;
  specialty: string;
  doctorName: string;
  appointmentDate: string;
  startTime: string;
  status: string;
  statusLabel: string;
}

export interface MedicalRecordResponse {
  id: string;
  examinationId: string | null;
  appointmentCode: string | null;
  doctorName: string;
  reason: string;
  examinationNotes: string;
  diagnosis: string;
  conclusion: string;
  treatmentPlan: string;
  prescription: string | null;
  followUpDate: string | null;
  signedAt: string;
}

export interface PatientNotificationResponse {
  id: string;
  type: string;
  title: string;
  message: string;
  targetUrl: string;
  read: boolean;
  createdAt: string;
}

export interface OperationalStatisticsResponse {
  from: string;
  to: string;
  specialty: string;
  doctorId: string | null;
  totalAppointments: number;
  checkedInAppointments: number;
  absentAppointments: number;
  cancelledAppointments: number;
  completedAppointments: number;
  notPerformedAppointments: number;
  averageWaitMinutes: number | null;
  averageExaminationMinutes: number | null;
}

export interface CreateAppointmentRequest {
  specialty: string;
  doctorName: string;
  appointmentDate: string;
  startTime: string;
  reason: string;
  profileId?: string;
  doctorId?: string;
  holdId?: string;
  serviceId?: string;
}

export interface CreateAppointmentHoldRequest {
  specialty: string;
  doctorName: string;
  appointmentDate: string;
  startTime: string;
  doctorId?: string;
  serviceId?: string;
}

export interface AppointmentHoldResponse {
  id: string;
  specialty: string;
  doctorName: string;
  appointmentDate: string;
  startTime: string;
  expiresAt: string;
}

export interface PatientProfileRequest {
  fullName: string;
  relationship: string;
  dateOfBirth: string;
  gender: string;
  phone?: string;
  identityNumber?: string;
  nationality?: string;
  ethnicity?: string;
  address?: string;
  provinceCode?: string;
  provinceName?: string;
  districtCode?: string;
  districtName?: string;
  wardCode?: string;
  wardName?: string;
  streetAddress?: string;
}

export interface SpecialtyOption {
  code: string;
  name: string;
  description: string;
}

export interface AppointmentSlotResponse {
  specialty: string;
  appointmentDate: string;
  startTime: string;
  endTime: string;
  doctorName: string;
  remainingCapacity: number;
  doctorId?: string | null;
  roomCode?: string | null;
}

export interface QueueTicketResponse {
  id: string;
  queueNumber: number;
  roomCode: string;
  roomName: string;
  queueDate: string;
  appointmentTime: string;
  status: 'WAITING' | 'CALLED' | 'IN_SERVICE' | 'SKIPPED' | 'COMPLETED' | 'CLOSED' | 'LEFT_BEFORE_EXAM' | string;
  statusLabel: string;
  presenceStatus?: 'READY' | 'RETURN_REQUIRED' | string;
  presenceLabel?: string;
  returnedAt?: string | null;
  appointmentCode: string;
  specialty: string;
  doctorName: string;
}

export interface DoctorQueueResponse {
  roomCode: string;
  roomName: string;
  specialty: string;
  tickets: QueueTicketResponse[];
}

export interface ClinicRoomResponse {
  id: string;
  code: string;
  name: string;
  specialty: string;
  active: boolean;
  qrToken: string;
}

export interface ClinicRoomCheckInResponse {
  code: string;
  name: string;
  specialty: string;
}

export interface DoctorAccountResponse {
  staffId: string;
  username: string;
  fullName: string;
  specialty: string | null;
  roomId: string | null;
  roomCode: string | null;
  roomName: string | null;
  assigned: boolean;
  active: boolean;
}

export interface EligibleDoctorResponse {
  doctorProfileId: string;
  staffId: string;
  fullName: string;
}

export interface ClinicServiceResponse {
  id: string;
  name: string;
  specialty: string;
  visitType: string;
  durationMinutes: number;
  active: boolean;
  eligibleDoctors: EligibleDoctorResponse[];
}

export interface ClinicServiceRequest {
  name: string;
  specialty: string;
  visitType: string;
  durationMinutes: number;
  doctorIds: string[];
}

export interface CreateDoctorRequest {
  username: string;
  fullName: string;
  password: string;
}

export interface DoctorAssignmentResponse {
  staffId: string;
  username: string;
  fullName: string;
  specialty: string;
  roomId: string;
  roomCode: string;
  roomName: string;
  active: boolean;
}

export interface DoctorScheduleResponse {
  id: string;
  doctorId: string;
  dayOfWeek: string;
  startTime: string;
  endTime: string;
  slotDurationMinutes: number;
  active: boolean;
}

export interface StaffAccountResponse {
  staffId: string;
  username: string;
  fullName: string;
  role: 'ADMIN' | 'COORDINATOR' | 'RECEPTIONIST' | 'DOCTOR' | string;
  status: 'ACTIVE' | 'LOCKED' | string;
}

export interface RescheduleCaseResponse {
  id: string;
  appointmentId: string;
  appointmentCode: string;
  specialty: string;
  oldDoctorName: string;
  oldDoctorId: string | null;
  oldAppointmentDate: string;
  oldStartTime: string;
  reason: string;
  status: 'OPEN' | 'RESOLVED' | string;
  newDoctorName: string | null;
  newDoctorId: string | null;
  newAppointmentDate: string | null;
  newStartTime: string | null;
  createdAt: string;
  resolvedAt: string | null;
}

export interface AvailableReplacementSlot {
  specialty: string;
  appointmentDate: string;
  startTime: string;
  endTime: string;
  doctorName: string;
  remainingCapacity: number;
  doctorId: string | null;
  roomCode: string | null;
}

export interface DoctorExaminationResponse {
  ticketId: string;
  appointmentId: string;
  examinationId: string;
  queueNumber: number;
  roomName: string;
  appointmentCode: string;
  specialty: string;
  doctorName: string;
  appointmentDate: string;
  startTime: string;
  patientName: string;
  patientDateOfBirth: string | null;
  patientGender: string | null;
  patientPhone: string;
  reason: string | null;
  examinationNotes: string | null;
  diagnosis: string | null;
  conclusion: string | null;
  treatmentPlan: string | null;
  prescription: string | null;
  followUpDate: string | null;
  status: string;
  signedAt: string | null;
}

export interface DoctorExaminationRequest {
  reason?: string;
  examinationNotes?: string;
  diagnosis?: string;
  conclusion?: string;
  treatmentPlan?: string;
  prescription?: string;
  followUpDate?: string | null;
}

export interface ReceptionAppointmentResponse {
  id: string;
  appointmentCode: string;
  appointmentDate: string;
  startTime: string;
  specialty: string;
  doctorName: string;
  roomCode: string | null;
  roomName: string | null;
  patientProfileId: string | null;
  patientName: string;
  patientPhone: string | null;
  status: string;
  queueNumber: number | null;
  queueStatus: string | null;
  queueStatusLabel: string | null;
  queuePresenceStatus?: 'READY' | 'RETURN_REQUIRED' | string | null;
  queuePresenceLabel?: string | null;
}

export interface ReceptionWalkInRequest {
  phone: string;
  profileId?: string | null;
  doctorId: string;
  appointmentDate: string;
  startTime: string;
  reason: string;
  exceptionReason: string;
}

export interface ReceptionPatientProfile {
  id: string;
  fullName: string;
  relationship: string;
  dateOfBirth?: string | null;
  primaryProfile: boolean;
  accountStatus?: string;
  mustChangePassword?: boolean;
}

export interface ReceptionPatientRegistrationRequest {
  phone: string;
  otpCode: string;
  fullName: string;
  dateOfBirth: string;
  gender: string;
  identityNumber?: string;
  nationality?: string;
  ethnicity?: string;
  address?: string;
}

export interface ReceptionPatientRegistrationResponse {
  accountId: string;
  phone: string;
  fullName: string;
  mustChangePassword: boolean;
}

export interface StaffLoginResponse {
  accessToken: string;
  tokenType: string;
  expiresAt: string;
  staffId: string;
  fullName: string;
  role: string;
}

export type ApiErrorResponse = {
  error?: {
    message?: string;
    detail?: string;
    title?: string;
    error?: { message?: string; detail?: string };
  } | string;
  message?: string;
  detail?: string;
};

export function apiErrorMessage(response: ApiErrorResponse): string {
  const payload = typeof response.error === 'object' && response.error !== null ? response.error : undefined;
  return payload?.message
    ?? payload?.detail
    ?? payload?.error?.message
    ?? payload?.error?.detail
    ?? response.message
    ?? response.detail
    ?? (typeof response.error === 'string' ? response.error : undefined)
    ?? 'Không thể xử lý yêu cầu. Vui lòng thử lại.';
}

@Injectable({ providedIn: 'root' })
export class AuthApiService {
  private readonly http = inject(HttpClient);
  private readonly apiRoot = '/api/v1/auth';
  private readonly appointmentsRoot = '/api/v1/appointments';
  private readonly examinationsRoot = '/api/v1/examinations';
  private readonly medicalRecordsRoot = '/api/v1/medical-records';
  private readonly patientProfilesRoot = '/api/v1/patient-profiles';
  private readonly specialtiesRoot = '/api/v1/specialties';
  private readonly appointmentSlotsRoot = '/api/v1/appointment-slots';
  private readonly appointmentHoldsRoot = '/api/v1/appointment-holds';
  private readonly queueRoot = '/api/v1/queue';
  private readonly notificationsRoot = '/api/v1/notifications';

  requestSmsOtp(phone: string, purpose: OtpPurpose): Observable<OtpResponse> {
    return this.http.post<OtpResponse>(`${this.apiRoot}/request-sms-otp`, { phone, purpose });
  }

  checkPhone(phone: string): Observable<CheckPhoneResponse> {
    return this.http.post<CheckPhoneResponse>(`${this.apiRoot}/check-phone`, { phone });
  }

  login(phone: string, password: string): Observable<SmsLoginResponse> {
    return this.http
      .post<SmsLoginResponse>(`${this.apiRoot}/login`, { phone, password })
      .pipe(tap((session) => {
        sessionStorage.setItem('clinicOneAccessToken', session.accessToken);
        sessionStorage.setItem('clinicOnePatientName', session.fullName);
        sessionStorage.removeItem('clinicOneStaffRole');
      }));
  }

  loginBySmsOtp(phone: string, password: string, code: string): Observable<SmsLoginResponse> {
    return this.http
      .post<SmsLoginResponse>(`${this.apiRoot}/login-sms`, { phone, password, code })
      .pipe(tap((session) => {
        sessionStorage.setItem('clinicOneAccessToken', session.accessToken);
        sessionStorage.setItem('clinicOnePatientName', session.fullName);
        sessionStorage.removeItem('clinicOneStaffRole');
      }));
  }

  verifySmsOtp(phone: string, purpose: OtpPurpose, code: string): Observable<{ verified: boolean }> {
    return this.http.post<{ verified: boolean }>(`${this.apiRoot}/verify-sms-otp`, { phone, purpose, code });
  }

  register(phone: string, fullName: string, password: string, dateOfBirth: string, gender: string, address: string,
           provinceCode = '', provinceName = '', districtCode = '', districtName = '', wardCode = '', wardName = '', streetAddress = ''): Observable<RegistrationResponse> {
    return this.http.post<RegistrationResponse>(`${this.apiRoot}/register`, {
      phone, fullName, password, dateOfBirth, gender, address,
      provinceCode, provinceName, districtCode, districtName, wardCode, wardName, streetAddress,
    });
  }

  getProfile(): Observable<PatientProfileResponse> {
    return this.http.get<PatientProfileResponse>(`${this.apiRoot}/me`);
  }

  getPatientProfiles(): Observable<PatientProfileItem[]> {
    return this.http.get<PatientProfileItem[]>(this.patientProfilesRoot);
  }

  getSpecialties(query?: string): Observable<SpecialtyOption[]> {
    return this.http.get<SpecialtyOption[]>(this.specialtiesRoot, query ? { params: { query } } : undefined);
  }

  getActiveClinicServices(): Observable<ClinicServiceResponse[]> {
    return this.http.get<ClinicServiceResponse[]>('/api/v1/services');
  }

  getAppointmentSlots(specialty: string, from: string, to: string, serviceId?: string): Observable<AppointmentSlotResponse[]> {
    return this.http.get<AppointmentSlotResponse[]>(this.appointmentSlotsRoot, {
      params: { specialty, from, to, ...(serviceId ? { serviceId } : {}) },
    });
  }

  createPatientProfile(request: PatientProfileRequest): Observable<PatientProfileItem> {
    return this.http.post<PatientProfileItem>(this.patientProfilesRoot, request);
  }

  updatePatientProfile(id: string, request: PatientProfileRequest): Observable<PatientProfileItem> {
    return this.http.patch<PatientProfileItem>(`${this.patientProfilesRoot}/${id}`, request);
  }

  deletePatientProfile(id: string): Observable<void> {
    return this.http.delete<void>(`${this.patientProfilesRoot}/${id}`);
  }

  updateProfile(fullName: string, dateOfBirth: string | null, gender: string | null, address: string,
               identityNumber: string, nationality: string, ethnicity: string, provinceCode: string,
               provinceName: string, districtCode: string, districtName: string, wardCode: string,
               wardName: string, streetAddress: string): Observable<PatientProfileResponse> {
    return this.http.patch<PatientProfileResponse>(`${this.apiRoot}/me`, {
      fullName, dateOfBirth, gender, address, identityNumber, nationality, ethnicity,
      provinceCode, provinceName, districtCode, districtName, wardCode, wardName, streetAddress,
    });
  }

  changePassword(currentPassword: string, newPassword: string): Observable<void> {
    return this.http.post<void>(`${this.apiRoot}/me/password`, { currentPassword, newPassword });
  }

  getAppointments(): Observable<AppointmentResponse[]> {
    return this.http.get<AppointmentResponse[]>(this.appointmentsRoot);
  }

  createAppointment(request: CreateAppointmentRequest): Observable<AppointmentResponse> {
    return this.http.post<AppointmentResponse>(this.appointmentsRoot, request);
  }

  getAppointment(id: string): Observable<AppointmentResponse> {
    return this.http.get<AppointmentResponse>(`${this.appointmentsRoot}/${id}`);
  }

  cancelAppointment(id: string, reason?: string): Observable<void> {
    return this.http.post<void>(`${this.appointmentsRoot}/${id}/cancel`, { reason: reason ?? '' });
  }

  rescheduleAppointment(id: string, appointmentDate: string, startTime: string): Observable<AppointmentResponse> {
    return this.http.post<AppointmentResponse>(`${this.appointmentsRoot}/${id}/reschedule`, { appointmentDate, startTime });
  }

  getExaminations(): Observable<ExaminationSessionResponse[]> {
    return this.http.get<ExaminationSessionResponse[]>(this.examinationsRoot);
  }

  getMedicalRecords(): Observable<MedicalRecordResponse[]> {
    return this.http.get<MedicalRecordResponse[]>(this.medicalRecordsRoot);
  }

  getMedicalRecord(id: string): Observable<MedicalRecordResponse> {
    return this.http.get<MedicalRecordResponse>(`${this.medicalRecordsRoot}/${id}`);
  }

  holdAppointmentSlot(request: CreateAppointmentHoldRequest): Observable<AppointmentHoldResponse> {
    return this.http.post<AppointmentHoldResponse>(this.appointmentHoldsRoot, request);
  }

  getNotifications(): Observable<PatientNotificationResponse[]> {
    return this.http.get<PatientNotificationResponse[]>(this.notificationsRoot);
  }

  getUnreadNotificationCount(): Observable<{ count: number }> {
    return this.http.get<{ count: number }>(`${this.notificationsRoot}/unread-count`);
  }

  markNotificationRead(id: string): Observable<void> {
    return this.http.post<void>(`${this.notificationsRoot}/${id}/read`, {});
  }

  checkInToRoom(roomCode: string, appointmentId: string): Observable<QueueTicketResponse> {
    return this.http.post<QueueTicketResponse>(`/api/v1/rooms/${encodeURIComponent(roomCode)}/queue/check-in`, { appointmentId });
  }

  getMyQueue(date: string): Observable<QueueTicketResponse[]> {
    return this.http.get<QueueTicketResponse[]>('/api/v1/patient/queue', { params: { date } });
  }

  getRoomForCheckIn(roomKey: string): Observable<ClinicRoomCheckInResponse> {
    return this.http.get<ClinicRoomCheckInResponse>(`/api/v1/rooms/${encodeURIComponent(roomKey)}/check-in`);
  }

  getRoomQueue(roomCode: string, date: string): Observable<QueueTicketResponse[]> {
    return this.http.get<QueueTicketResponse[]>(`/api/v1/rooms/${encodeURIComponent(roomCode)}/queue`, { params: { date } });
  }

  getDoctorQueue(date: string): Observable<DoctorQueueResponse> {
    return this.http.get<DoctorQueueResponse>('/api/v1/doctor/queue', { params: { date } });
  }

  callQueueTicket(ticketId: string): Observable<QueueTicketResponse> {
    return this.http.post<QueueTicketResponse>(`${this.queueRoot}/${ticketId}/call`, {});
  }

  skipQueueTicket(ticketId: string, reason = ''): Observable<QueueTicketResponse> {
    return this.http.post<QueueTicketResponse>(`${this.queueRoot}/${ticketId}/skip`, { reason });
  }

  leaveQueueTicket(ticketId: string, reason: string): Observable<QueueTicketResponse> {
    return this.http.post<QueueTicketResponse>(`${this.queueRoot}/${ticketId}/leave`, { reason });
  }

  startQueueTicket(ticketId: string): Observable<QueueTicketResponse> {
    return this.http.post<QueueTicketResponse>(`${this.queueRoot}/${ticketId}/start`, {});
  }

  completeQueueTicket(ticketId: string): Observable<QueueTicketResponse> {
    return this.http.post<QueueTicketResponse>(`${this.queueRoot}/${ticketId}/complete`, {});
  }

  getRooms(): Observable<ClinicRoomResponse[]> {
    return this.http.get<ClinicRoomResponse[]>('/api/v1/rooms');
  }

  getDoctorExamination(ticketId: string): Observable<DoctorExaminationResponse> {
    return this.http.get<DoctorExaminationResponse>(`/api/v1/doctor/examinations/${ticketId}`);
  }

  callNextDoctor(date: string): Observable<QueueTicketResponse> {
    return this.http.post<QueueTicketResponse>('/api/v1/doctor/queue/call-next', {}, { params: { date } });
  }

  searchReceptionAppointments(query: string, date: string): Observable<ReceptionAppointmentResponse[]> {
    return this.http.get<ReceptionAppointmentResponse[]>('/api/v1/reception/appointments', { params: { query, date } });
  }

  receptionCheckIn(appointmentId: string, roomCode: string, reason: string): Observable<ReceptionAppointmentResponse> {
    return this.http.post<ReceptionAppointmentResponse>(`/api/v1/reception/appointments/${appointmentId}/check-in`, { roomCode, reason });
  }

  leaveReceptionAppointment(appointmentId: string, reason: string): Observable<ReceptionAppointmentResponse> {
    return this.http.post<ReceptionAppointmentResponse>(`/api/v1/reception/appointments/${appointmentId}/leave`, { reason });
  }

  createReceptionWalkIn(request: ReceptionWalkInRequest): Observable<ReceptionAppointmentResponse> {
    return this.http.post<ReceptionAppointmentResponse>('/api/v1/reception/walk-in', request);
  }

  getReceptionProfiles(phone: string): Observable<ReceptionPatientProfile[]> {
    return this.http.get<ReceptionPatientProfile[]>('/api/v1/reception/profiles', { params: { phone } });
  }

  requestReceptionPatientOtp(phone: string): Observable<{ expiresInSeconds: number; retryAfterSeconds: number }> {
    return this.http.post<{ expiresInSeconds: number; retryAfterSeconds: number }>('/api/v1/reception/patients/request-otp', { phone });
  }

  registerReceptionPatient(request: ReceptionPatientRegistrationRequest): Observable<ReceptionPatientRegistrationResponse> {
    return this.http.post<ReceptionPatientRegistrationResponse>('/api/v1/reception/patients', request);
  }

  saveDoctorExaminationDraft(ticketId: string, request: DoctorExaminationRequest): Observable<DoctorExaminationResponse> {
    return this.http.put<DoctorExaminationResponse>(`/api/v1/doctor/examinations/${ticketId}/draft`, request);
  }

  signDoctorExamination(ticketId: string, request: DoctorExaminationRequest): Observable<DoctorExaminationResponse> {
    return this.http.post<DoctorExaminationResponse>(`/api/v1/doctor/examinations/${ticketId}/sign`, request);
  }

  staffLogin(username: string, password: string): Observable<StaffLoginResponse> {
    return this.http.post<StaffLoginResponse>('/api/v1/staff/auth/login', { username, password }).pipe(tap((session) => {
      sessionStorage.setItem('clinicOneAccessToken', session.accessToken);
      sessionStorage.setItem('clinicOnePatientName', session.fullName);
      sessionStorage.setItem('clinicOneStaffRole', session.role);
    }));
  }

  createRoom(request: Omit<ClinicRoomResponse, 'id' | 'active' | 'qrToken'>): Observable<ClinicRoomResponse> {
    return this.http.post<ClinicRoomResponse>('/api/v1/rooms', request);
  }

  updateRoom(id: string, request: Omit<ClinicRoomResponse, 'id' | 'active' | 'qrToken'>): Observable<ClinicRoomResponse> {
    return this.http.put<ClinicRoomResponse>(`/api/v1/rooms/${id}`, request);
  }

  setRoomActive(id: string, active: boolean): Observable<ClinicRoomResponse> {
    return this.http.post<ClinicRoomResponse>(`/api/v1/rooms/${id}/${active ? 'activate' : 'deactivate'}`, {});
  }

  getDoctors(): Observable<DoctorAccountResponse[]> {
    return this.http.get<DoctorAccountResponse[]>('/api/v1/admin/doctors');
  }

  getClinicServices(activeOnly = false): Observable<ClinicServiceResponse[]> {
    return this.http.get<ClinicServiceResponse[]>(`/api/v1/admin/services${activeOnly ? '/active' : ''}`);
  }

  createClinicService(request: ClinicServiceRequest): Observable<ClinicServiceResponse> {
    return this.http.post<ClinicServiceResponse>('/api/v1/admin/services', request);
  }

  updateClinicService(id: string, request: ClinicServiceRequest): Observable<ClinicServiceResponse> {
    return this.http.put<ClinicServiceResponse>(`/api/v1/admin/services/${id}`, request);
  }

  setClinicServiceActive(id: string, active: boolean): Observable<ClinicServiceResponse> {
    return this.http.post<ClinicServiceResponse>(`/api/v1/admin/services/${id}/${active ? 'activate' : 'deactivate'}`, {});
  }

  createDoctor(request: CreateDoctorRequest): Observable<DoctorAccountResponse> {
    return this.http.post<DoctorAccountResponse>('/api/v1/admin/doctors', request);
  }

  assignDoctor(staffId: string, specialty: string, roomId: string): Observable<DoctorAssignmentResponse> {
    return this.http.put<DoctorAssignmentResponse>(`/api/v1/admin/doctors/${staffId}/assignment`, { specialty, roomId });
  }

  getDoctorSchedules(staffId: string): Observable<DoctorScheduleResponse[]> {
    return this.http.get<DoctorScheduleResponse[]>(`/api/v1/admin/doctors/${staffId}/schedules`);
  }

  addDoctorSchedule(staffId: string, request: { dayOfWeek: string; startTime: string; endTime: string; slotDurationMinutes: number }): Observable<DoctorScheduleResponse> {
    return this.http.post<DoctorScheduleResponse>(`/api/v1/admin/doctors/${staffId}/schedules`, request);
  }

  removeDoctorSchedule(staffId: string, scheduleId: string): Observable<void> {
    return this.http.delete<void>(`/api/v1/admin/doctors/${staffId}/schedules/${scheduleId}`);
  }

  getStaffAccounts(): Observable<StaffAccountResponse[]> {
    return this.http.get<StaffAccountResponse[]>('/api/v1/admin/staff');
  }

  lockStaffAccount(staffId: string): Observable<StaffAccountResponse> {
    return this.http.post<StaffAccountResponse>(`/api/v1/admin/staff/${staffId}/lock`, {});
  }

  unlockStaffAccount(staffId: string): Observable<StaffAccountResponse> {
    return this.http.post<StaffAccountResponse>(`/api/v1/admin/staff/${staffId}/unlock`, {});
  }

  getRescheduleCases(): Observable<RescheduleCaseResponse[]> {
    return this.http.get<RescheduleCaseResponse[]>('/api/v1/admin/rescheduling');
  }

  getReplacementSlots(caseId: string, from?: string, to?: string): Observable<AvailableReplacementSlot[]> {
    let params: Record<string, string> | undefined;
    if (from && to) params = { from, to };
    return this.http.get<AvailableReplacementSlot[]>(`/api/v1/admin/rescheduling/${caseId}/alternatives`, { params });
  }

  resolveRescheduleCase(caseId: string, appointmentDate: string, startTime: string,
                        doctorName: string, doctorId?: string | null): Observable<RescheduleCaseResponse> {
    return this.http.post<RescheduleCaseResponse>(`/api/v1/admin/rescheduling/${caseId}/resolve`, {
      appointmentDate, startTime, doctorName, doctorId: doctorId || undefined,
    });
  }

  getOperationalStatistics(from: string, to: string, specialty: string, doctorId?: string): Observable<OperationalStatisticsResponse> {
    let params: Record<string, string> = { from, to, specialty };
    if (doctorId) params = { ...params, doctorId };
    return this.http.get<OperationalStatisticsResponse>('/api/v1/admin/statistics', { params });
  }
}
