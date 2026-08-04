import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

export const authGuard: CanActivateFn = (_route, state) => {
  const router = inject(Router);
  const token = typeof sessionStorage === 'undefined' ? null : sessionStorage.getItem('clinicOneAccessToken');

  return token ? true : router.createUrlTree(['/login'], { queryParams: { returnUrl: state.url } });
};
