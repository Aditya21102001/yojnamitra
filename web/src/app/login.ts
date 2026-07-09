import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { Auth } from './auth';
import { I18n } from './i18n';

@Component({
  selector: 'app-login',
  imports: [FormsModule, RouterLink],
  templateUrl: './login.html',
})
export class Login {
  private readonly auth = inject(Auth);
  private readonly router = inject(Router);
  readonly i18n = inject(I18n);

  username = '';
  password = '';
  code = '';

  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  /** Non-null once the password was accepted but a second factor is still owed. */
  readonly mfaToken = signal<string | null>(null);

  submit(): void {
    if (!this.username.trim() || !this.password) {
      return;
    }
    this.loading.set(true);
    this.error.set(null);
    this.auth.login(this.username.trim(), this.password).subscribe({
      next: (res) => {
        this.loading.set(false);
        if (res.mfaRequired && res.mfaToken) {
          this.mfaToken.set(res.mfaToken);
          this.password = '';
        } else {
          this.router.navigateByUrl('/dashboard');
        }
      },
      error: (err) => {
        this.error.set(this.message(err));
        this.loading.set(false);
      },
    });
  }

  verify(): void {
    const token = this.mfaToken();
    if (!token || !this.code.trim()) {
      return;
    }
    this.loading.set(true);
    this.error.set(null);
    this.auth.verifyMfa(token, this.code.trim()).subscribe({
      next: () => this.router.navigateByUrl('/dashboard'),
      error: (err) => {
        this.error.set(this.message(err, true));
        this.loading.set(false);
        this.code = '';
      },
    });
  }

  /** Back to the password step; the challenge token is discarded, not reused. */
  cancelMfa(): void {
    this.mfaToken.set(null);
    this.code = '';
    this.error.set(null);
  }

  /** `mfaStep` picks the right 401 wording: at that point the password already passed. */
  private message(err: unknown, mfaStep = false): string {
    const e = err as { status?: number; error?: { message?: string; detail?: string } };
    if (e?.status === 429) {
      return this.i18n.t('mfa.tooManyAttempts');
    }
    if (e?.status === 401) {
      return this.i18n.t(mfaStep ? 'mfa.invalidCode' : 'auth.invalidCreds');
    }
    return e?.error?.message || e?.error?.detail || this.i18n.t('auth.loginFailed');
  }
}
