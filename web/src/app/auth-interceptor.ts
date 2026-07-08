import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Auth } from './auth';

/** Attaches the Bearer token (when present) to every outgoing request. */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const token = inject(Auth).token();
  if (token) {
    req = req.clone({ setHeaders: { Authorization: `Bearer ${token}` } });
  }
  return next(req);
};
