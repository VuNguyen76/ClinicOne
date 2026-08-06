import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';

export const authTokenInterceptor: HttpInterceptorFn = (request, next) => {
  const router = inject(Router);
  const token = sessionStorage.getItem('clinicOneAccessToken');
  // Never forward a ClinicOne bearer token to third-party APIs. Apart from
  // leaking a credential, the Authorization header turns a simple GET into a
  // CORS preflight that public data APIs are not required to support.
  const isClinicOneApi = request.url.startsWith('/api/');
  if (!token || !isClinicOneApi || request.url.includes('/auth/login') || request.url.includes('/auth/register')) {
    return next(request);
  }

  return next(request.clone({
    setHeaders: { Authorization: `Bearer ${token}` },
  })).pipe(
    catchError((error: unknown) => {
      // A stale/expired session must not leave the user on a broken account
      // screen. Clear it once and let the login page establish a fresh session.
      const isCurrentSessionRequest = request.url.includes('/auth/me');
      if (error instanceof HttpErrorResponse && (error.status === 401 || (error.status === 403 && isCurrentSessionRequest))) {
        sessionStorage.removeItem('clinicOneAccessToken');
        sessionStorage.removeItem('clinicOnePatientName');
        sessionStorage.removeItem('clinicOneStaffRole');
        if (!router.url.startsWith('/login')) {
          void router.navigate(['/login'], { queryParams: { returnUrl: router.url } });
        }
      }
      return throwError(() => error);
    }),
  );
};
