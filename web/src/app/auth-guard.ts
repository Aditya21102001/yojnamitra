import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { Auth } from './auth';

/** Blocks routes that require a logged-in user, redirecting to /login. */
export const authGuard: CanActivateFn = () => {
  const auth = inject(Auth);
  const router = inject(Router);
  return auth.isLoggedIn() ? true : router.createUrlTree(['/login']);
};
