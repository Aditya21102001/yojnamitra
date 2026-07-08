import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';
import { AuthResponse } from './models';

const TOKEN_KEY = 'ym_token';
const USER_KEY = 'ym_user';

/** Holds the JWT + current user, persisted in localStorage. */
@Injectable({ providedIn: 'root' })
export class Auth {
  private readonly http = inject(HttpClient);
  private readonly base = 'http://localhost:8080/api/auth';

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

  login(username: string, password: string): Observable<AuthResponse> {
    return this.http
      .post<AuthResponse>(`${this.base}/login`, { username, password })
      .pipe(tap((res) => this.setSession(res)));
  }

  logout(): void {
    this._token.set(null);
    this._username.set(null);
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
  }

  private setSession(res: AuthResponse): void {
    this._token.set(res.token);
    this._username.set(res.username);
    localStorage.setItem(TOKEN_KEY, res.token);
    localStorage.setItem(USER_KEY, res.username);
  }
}
