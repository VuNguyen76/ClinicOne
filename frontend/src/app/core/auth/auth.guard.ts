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

export const receptionGuard: CanActivateFn = (_route, state) => {
  const router = inject(Router);
  const token = sessionToken();
  const role = staffRole();
  if (!token || !role) {
    return router.createUrlTree(['/staff/login'], { queryParams: { returnUrl: state.url } });
  }
  return ['ADMIN', 'COORDINATOR', 'RECEPTIONIST'].includes(role)
    ? true
    : router.createUrlTree(['/doctor']);
};

export const doctorGuard: CanActivateFn = (_route, state) => {
  const router = inject(Router);
  const token = sessionToken();
  const role = staffRole();

  if (!token || !role) {
    return router.createUrlTree(['/staff/login'], { queryParams: { returnUrl: state.url } });
  }
  return role === 'DOCTOR' ? true : router.createUrlTree(['/home']);
};

export const homeGuard: CanActivateFn = () => {
  const router = inject(Router);
  const role = staffRole();
  if (!sessionToken() || !role) {
    return true;
  }
  if (role === 'DOCTOR') {
    return router.createUrlTree(['/doctor']);
  }
  if (role === 'ADMIN' || role === 'COORDINATOR') {
    return router.createUrlTree(['/admin/rooms']);
  }
  return router.createUrlTree(['/staff/login']);
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

export const adminGuard: CanActivateFn = (_route, state) => {
  const router = inject(Router);
  const token = sessionToken();
  const role = staffRole();

  if (!token || !role) {
    return router.createUrlTree(['/staff/login'], { queryParams: { returnUrl: state.url } });
  }
  return role === 'ADMIN' ? true : router.createUrlTree(['/admin/rooms']);
};

function sessionToken(): string | null {
  return typeof sessionStorage === 'undefined' ? null : sessionStorage.getItem('clinicOneAccessToken');
}

function staffRole(): string | null {
  return typeof sessionStorage === 'undefined' ? null : sessionStorage.getItem('clinicOneStaffRole');
}
