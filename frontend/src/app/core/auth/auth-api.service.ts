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
}

export interface CreateAppointmentRequest {
  specialty: string;
  doctorName: string;
  appointmentDate: string;
  startTime: string;
  reason: string;
}

export type ApiErrorResponse = {
  error?: { message?: string; detail?: string; title?: string } | string;
  message?: string;
  detail?: string;
};

export function apiErrorMessage(response: ApiErrorResponse): string {
  const payload = typeof response.error === 'object' && response.error !== null ? response.error : undefined;
  return payload?.message ?? payload?.detail ?? response.message ?? response.detail ?? (typeof response.error === 'string' ? response.error : undefined) ?? 'Không thể xử lý yêu cầu. Vui lòng thử lại.';
}

@Injectable({ providedIn: 'root' })
export class AuthApiService {
  private readonly http = inject(HttpClient);
  private readonly apiRoot = '/api/v1/auth';
  private readonly appointmentsRoot = '/api/v1/appointments';

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
      }));
  }

  loginBySmsOtp(phone: string, password: string, code: string): Observable<SmsLoginResponse> {
    return this.http
      .post<SmsLoginResponse>(`${this.apiRoot}/login-sms`, { phone, password, code })
      .pipe(tap((session) => {
        sessionStorage.setItem('clinicOneAccessToken', session.accessToken);
        sessionStorage.setItem('clinicOnePatientName', session.fullName);
      }));
  }

  verifySmsOtp(phone: string, purpose: OtpPurpose, code: string): Observable<{ verified: boolean }> {
    return this.http.post<{ verified: boolean }>(`${this.apiRoot}/verify-sms-otp`, { phone, purpose, code });
  }

  register(phone: string, fullName: string, password: string, dateOfBirth: string, gender: string, address: string): Observable<RegistrationResponse> {
    return this.http.post<RegistrationResponse>(`${this.apiRoot}/register`, { phone, fullName, password, dateOfBirth, gender, address });
  }

  getProfile(): Observable<PatientProfileResponse> {
    return this.http.get<PatientProfileResponse>(`${this.apiRoot}/me`);
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
}
