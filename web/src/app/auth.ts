import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';
import { AuthResponse, MfaSetup, MfaStatus } from './models';
import { environment } from '../environments/environment';

const TOKEN_KEY = 'ym_token';
const USER_KEY = 'ym_user';

/** Holds the JWT + current user, persisted in localStorage. */
@Injectable({ providedIn: 'root' })
export class Auth {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBase}/auth`;

  private readonly _token = signal<string | null>(localStorage.getItem(TOKEN_KEY));
  private readonly _username = signal<string | null>(localStorage.getItem(USER_KEY));

  readonly username = this._username.asReadonly();
  readonly isLoggedIn = computed(() => !!this._token());

  token(): string | null {
    return this._token();
  }

  register(username: string, password: string): Observable<AuthResponse> {
    return this.http
      .post<AuthResponse>(`${this.base}/register`, { username, password })
      .pipe(tap((res) => this.setSession(res)));
  }

  /**
   * First factor only. When the account has MFA enabled the response carries a
   * challenge token and no session, so nothing is persisted here — the caller
   * must complete `verifyMfa` before the user counts as logged in.
   */
  login(username: string, password: string): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${this.base}/login`, { username, password }).pipe(
      tap((res) => {
        if (!res.mfaRequired) {
          this.setSession(res);
        }
      }),
    );
  }

  /** Second factor: exchanges the challenge token plus a code for a real session. */
  verifyMfa(mfaToken: string, code: string): Observable<AuthResponse> {
    return this.http
      .post<AuthResponse>(`${this.base}/mfa/verify`, { mfaToken, code })
      .pipe(tap((res) => this.setSession(res)));
  }

  logout(): void {
    this._token.set(null);
    this._username.set(null);
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
  }

  // ---- enrolment (requires an active session; token added by the interceptor) ----

  mfaStatus(): Observable<MfaStatus> {
    return this.http.get<MfaStatus>(`${this.base}/mfa/status`);
  }

  mfaSetup(): Observable<MfaSetup> {
    return this.http.post<MfaSetup>(`${this.base}/mfa/setup`, {});
  }

  /** Resolves with the recovery codes — the only time the server reveals them. */
  mfaEnable(code: string): Observable<{ recoveryCodes: string[] }> {
    return this.http.post<{ recoveryCodes: string[] }>(`${this.base}/mfa/enable`, { code });
  }

  mfaDisable(password: string, code: string): Observable<{ enabled: boolean }> {
    return this.http.post<{ enabled: boolean }>(`${this.base}/mfa/disable`, { password, code });
  }

  private setSession(res: AuthResponse): void {
    if (!res.token) {
      return;
    }
    this._token.set(res.token);
    this._username.set(res.username);
    localStorage.setItem(TOKEN_KEY, res.token);
    localStorage.setItem(USER_KEY, res.username);
  }
}
