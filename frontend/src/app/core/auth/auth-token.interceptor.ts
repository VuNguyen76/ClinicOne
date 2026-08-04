import { HttpInterceptorFn } from '@angular/common/http';

export const authTokenInterceptor: HttpInterceptorFn = (request, next) => {
  const token = sessionStorage.getItem('clinicOneAccessToken');
  if (!token || request.url.includes('/auth/login') || request.url.includes('/auth/register')) {
    return next(request);
  }

  return next(request.clone({
    setHeaders: { Authorization: `Bearer ${token}` },
  }));
};
