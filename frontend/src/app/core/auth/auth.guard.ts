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

  if (!token) {
    return router.createUrlTree(['/login'], { queryParams: { returnUrl: state.url } });
  }
  return isStaffSession() ? router.createUrlTree(['/staff']) : true;
};

export const staffGuard: CanActivateFn = (_route, state) => {
  const router = inject(Router);
  const token = sessionToken();
  const roles = staffRoles();

  if (token && isStaffSession() && roles.length) {
    return true;
  }
  return router.createUrlTree(['/staff/login'], { queryParams: { returnUrl: state.url } });
};

export const receptionGuard: CanActivateFn = (_route, state) => {
  const router = inject(Router);
  const token = sessionToken();
  const roles = staffRoles();
  if (!token || !isStaffSession() || !roles.length) {
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

  if (!token || !isStaffSession() || !roles.length) {
    return router.createUrlTree(['/staff/login'], { queryParams: { returnUrl: state.url } });
  }
  return roles.includes('DOCTOR') ? true : router.createUrlTree(['/home']);
};

/**
 * The room queue is an operational workspace shared by the doctor who owns
 * the room and front-desk roles that monitor or adjust the queue. It must not
 * reuse doctorGuard: that guard intentionally blocks reception staff from the
 * doctor's clinical board.
 */
export const queueBoardGuard: CanActivateFn = (_route, state) => {
  const router = inject(Router);
  const token = sessionToken();
  const roles = staffRoles();

  if (!token || !isStaffSession() || !roles.length) {
    return router.createUrlTree(['/staff/login'], { queryParams: { returnUrl: state.url } });
  }
  return roles.some((role) => ['DOCTOR', 'COORDINATOR', 'RECEPTIONIST'].includes(role))
    ? true
    : router.createUrlTree(['/staff']);
};

export const homeGuard: CanActivateFn = () => {
  const router = inject(Router);
  const roles = staffRoles();
  if (!sessionToken() || !isStaffSession()) {
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
  if (!sessionToken() || !isStaffSession() || !roles.length) return '/staff/login';
  if (roles.includes('DOCTOR')) return '/doctor';
  if (roles.includes('ADMIN') || roles.includes('COORDINATOR')) return '/admin/rooms';
  if (roles.includes('RECEPTIONIST')) return '/reception/check-in';
  return '/staff/login';
};

export const roomManagerGuard: CanActivateFn = (_route, state) => {
  const router = inject(Router);
  const token = sessionToken();
  const roles = staffRoles();

  if (!token || !isStaffSession() || !roles.length) {
    return router.createUrlTree(['/staff/login'], { queryParams: { returnUrl: state.url } });
  }
  return roles.includes('ADMIN') || roles.includes('COORDINATOR') ? true : router.createUrlTree(['/home']);
};

export const adminGuard: CanActivateFn = (_route, state) => {
  const router = inject(Router);
  const token = sessionToken();
  const roles = staffRoles();

  if (!token || !isStaffSession() || !roles.length) {
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

/** Returns whether the active staff session has the requested operational role. */
export function hasStaffRole(role: string): boolean {
  return staffRoles().includes(role);
}

function isStaffSession(): boolean {
  if (typeof sessionStorage === 'undefined') return false;
  const sessionType = sessionStorage.getItem('clinicOneSessionType');
  if (sessionType) return sessionType === 'STAFF';
  // Older sessions did not persist an explicit type. Keep the role fallback
  // only for that legacy shape; an explicit PATIENT marker must win over any
  // stale role keys left by a previous staff session.
  return staffRoles().length > 0;
}
