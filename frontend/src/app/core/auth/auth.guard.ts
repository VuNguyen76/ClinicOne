import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

export const authGuard: CanActivateFn = (_route, state) => {
  const router = inject(Router);
  const token = sessionToken();

  return token ? true : router.createUrlTree(['/login'], { queryParams: { returnUrl: state.url } });
};

export const patientGuard: CanActivateFn = (_route, state) => {
  const router = inject(Router);
  const token = sessionToken();
  const role = staffRole();

  if (!token) {
    return router.createUrlTree(['/login'], { queryParams: { returnUrl: state.url } });
  }
  return role ? router.createUrlTree(['/home']) : true;
};

export const staffGuard: CanActivateFn = (_route, state) => {
  const router = inject(Router);
  const token = sessionToken();
  const role = staffRole();

  if (token && role) {
    return true;
  }
  return router.createUrlTree(['/staff/login'], { queryParams: { returnUrl: state.url } });
};

export const roomManagerGuard: CanActivateFn = (_route, state) => {
  const router = inject(Router);
  const token = sessionToken();
  const role = staffRole();

  if (!token || !role) {
    return router.createUrlTree(['/staff/login'], { queryParams: { returnUrl: state.url } });
  }
  return role === 'ADMIN' || role === 'COORDINATOR' ? true : router.createUrlTree(['/home']);
};

function sessionToken(): string | null {
  return typeof sessionStorage === 'undefined' ? null : sessionStorage.getItem('clinicOneAccessToken');
}

function staffRole(): string | null {
  return typeof sessionStorage === 'undefined' ? null : sessionStorage.getItem('clinicOneStaffRole');
}
