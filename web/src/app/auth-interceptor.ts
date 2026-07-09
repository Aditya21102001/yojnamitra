import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { Auth } from './auth';

/**
 * Endpoints where a 401 means "those credentials were wrong", not "your session
 * is dead". Signing the user out here would wipe the screen they are trying to
 * correct — and on /mfa/disable it would eject someone who merely mistyped a
 * digit while already authenticated.
 */
const EXPECTED_401 = [
  '/auth/login',
  '/auth/register',
  '/auth/mfa/verify',
  '/auth/mfa/enable',
  '/auth/mfa/disable',
];

/** Attaches the Bearer token (when present) and retires a session the server rejects. */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(Auth);
  const router = inject(Router);

  const token = auth.token();
  if (token) {
    req = req.clone({ setHeaders: { Authorization: `Bearer ${token}` } });
  }

  return next(req).pipe(
    catchError((err: HttpErrorResponse) => {
      // isLoggedIn() only checks that a token string exists. A token the server
      // no longer accepts — expired, or issued before the `typ` claim existed —
      // leaves the UI claiming to be signed in while every protected call fails.
      const credentialCheck = EXPECTED_401.some((path) => req.url.includes(path));
      if (err.status === 401 && token && !credentialCheck) {
        auth.logout();
        router.navigateByUrl('/login');
      }
      return throwError(() => err);
    }),
  );
};
