import { inject } from '@angular/core';
import { CanActivateFn, RedirectFunction, Router } from '@angular/router';

export const authGuard: CanActivateFn = (_route, state) => {
  const router = inject(Router);
  const token = sessionToken();

  return token ? true : router.createUrlTree(['/login'], { queryParams: { returnUrl: state.url } });
};

export const patientGuard: CanActivateFn = (_route, state) => {
  const router = inject(Router);
  const token = sessionToken();
  const roles = staffRoles();

  if (!token) {
    return router.createUrlTree(['/login'], { queryParams: { returnUrl: state.url } });
  }
  return roles.length ? router.createUrlTree(['/home']) : true;
};

export const staffGuard: CanActivateFn = (_route, state) => {
  const router = inject(Router);
  const token = sessionToken();
  const roles = staffRoles();

  if (token && roles.length) {
    return true;
  }
  return router.createUrlTree(['/staff/login'], { queryParams: { returnUrl: state.url } });
};

export const receptionGuard: CanActivateFn = (_route, state) => {
  const router = inject(Router);
  const token = sessionToken();
  const roles = staffRoles();
  if (!token || !roles.length) {
    return router.createUrlTree(['/staff/login'], { queryParams: { returnUrl: state.url } });
  }
  return roles.some((role) => ['COORDINATOR', 'RECEPTIONIST'].includes(role))
    ? true
    : router.createUrlTree(['/doctor']);
};

export const doctorGuard: CanActivateFn = (_route, state) => {
  const router = inject(Router);
  const token = sessionToken();
  const roles = staffRoles();

  if (!token || !roles.length) {
    return router.createUrlTree(['/staff/login'], { queryParams: { returnUrl: state.url } });
  }
  return roles.includes('DOCTOR') ? true : router.createUrlTree(['/home']);
};

export const homeGuard: CanActivateFn = () => {
  const router = inject(Router);
  const roles = staffRoles();
  if (!sessionToken() || !roles.length) {
    return true;
  }
  if (roles.includes('DOCTOR')) {
    return router.createUrlTree(['/doctor']);
  }
  if (roles.includes('ADMIN') || roles.includes('COORDINATOR')) {
    return router.createUrlTree(['/admin/rooms']);
  }
  if (roles.includes('RECEPTIONIST')) {
    return router.createUrlTree(['/reception/check-in']);
  }
  return router.createUrlTree(['/staff/login']);
};

export const staffLandingRedirect: RedirectFunction = () => {
  const roles = staffRoles();
  if (!sessionToken() || !roles.length) return '/staff/login';
  if (roles.includes('DOCTOR')) return '/doctor';
  if (roles.includes('ADMIN') || roles.includes('COORDINATOR')) return '/admin/rooms';
  if (roles.includes('RECEPTIONIST')) return '/reception/check-in';
  return '/staff/login';
};

export const roomManagerGuard: CanActivateFn = (_route, state) => {
  const router = inject(Router);
  const token = sessionToken();
  const roles = staffRoles();

  if (!token || !roles.length) {
    return router.createUrlTree(['/staff/login'], { queryParams: { returnUrl: state.url } });
  }
  return roles.includes('ADMIN') || roles.includes('COORDINATOR') ? true : router.createUrlTree(['/home']);
};

export const adminGuard: CanActivateFn = (_route, state) => {
  const router = inject(Router);
  const token = sessionToken();
  const roles = staffRoles();

  if (!token || !roles.length) {
    return router.createUrlTree(['/staff/login'], { queryParams: { returnUrl: state.url } });
  }
  return roles.includes('ADMIN') ? true : router.createUrlTree(['/admin/rooms']);
};

function sessionToken(): string | null {
  return typeof sessionStorage === 'undefined' ? null : sessionStorage.getItem('clinicOneAccessToken');
}

function staffRole(): string | null {
  return typeof sessionStorage === 'undefined' ? null : sessionStorage.getItem('clinicOneStaffRole');
}

function staffRoles(): string[] {
  if (typeof sessionStorage === 'undefined') return [];
  const raw = sessionStorage.getItem('clinicOneStaffRoles');
  if (raw) {
    try {
      const parsed = JSON.parse(raw);
      if (Array.isArray(parsed)) return parsed.filter((value): value is string => typeof value === 'string');
    } catch { /* fall back to the legacy primary role */ }
  }
  const role = staffRole();
  return role ? [role] : [];
}
