import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable, tap } from 'rxjs';

export type OtpPurpose = 'LOGIN' | 'REGISTRATION' | 'RECOVERY';

export interface OtpResponse {
  expiresInSeconds: number;
  retryAfterSeconds: number;
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

@Injectable({ providedIn: 'root' })
export class AuthApiService {
  private readonly http = inject(HttpClient);
  private readonly apiRoot = '/api/v1/auth';

  requestSmsOtp(phone: string, purpose: OtpPurpose): Observable<OtpResponse> {
    return this.http.post<OtpResponse>(`${this.apiRoot}/request-sms-otp`, { phone, purpose });
  }

  loginBySmsOtp(phone: string, password: string, code: string): Observable<SmsLoginResponse> {
    return this.http
      .post<SmsLoginResponse>(`${this.apiRoot}/login-sms`, { phone, password, code })
      .pipe(tap((session) => sessionStorage.setItem('clinicOneAccessToken', session.accessToken)));
  }

  verifySmsOtp(phone: string, purpose: OtpPurpose, code: string): Observable<{ verified: boolean }> {
    return this.http.post<{ verified: boolean }>(`${this.apiRoot}/verify-sms-otp`, { phone, purpose, code });
  }

  register(phone: string, fullName: string, password: string): Observable<RegistrationResponse> {
    return this.http.post<RegistrationResponse>(`${this.apiRoot}/register`, { phone, fullName, password });
  }
}
