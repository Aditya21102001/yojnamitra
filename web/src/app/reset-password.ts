import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Auth } from './auth';
import { I18n } from './i18n';

@Component({
  selector: 'app-reset-password',
  imports: [FormsModule, RouterLink],
  templateUrl: './reset-password.html',
})
export class ResetPassword implements OnInit {
  private readonly auth = inject(Auth);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  readonly i18n = inject(I18n);

  private token = '';
  password = '';
  code = '';

  /** null = still checking the link; false = link is dead; true = show the form. */
  readonly linkValid = signal<boolean | null>(null);
  readonly mfaRequired = signal(false);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly done = signal(false);

  ngOnInit(): void {
    this.token = this.route.snapshot.queryParamMap.get('token') ?? '';
    if (!this.token) {
      this.linkValid.set(false);
      return;
    }
    // Precheck tells us whether the link still works and whether this account
    // needs a 2FA code, so we can show the right form before the user types.
    this.auth.resetPrecheck(this.token).subscribe({
      next: (res) => {
        this.linkValid.set(res.valid);
        this.mfaRequired.set(res.mfaRequired);
      },
      error: () => this.linkValid.set(false),
    });
  }

  submit(): void {
    if (this.password.length < 6) {
      this.error.set(this.i18n.t('auth.passwordMin'));
      return;
    }
    if (this.mfaRequired() && !this.code.trim()) {
      return;
    }
    this.loading.set(true);
    this.error.set(null);
    this.auth.resetPassword(this.token, this.password, this.code.trim() || undefined).subscribe({
      next: () => {
        this.loading.set(false);
        this.done.set(true);
      },
      error: (err) => {
        this.error.set(this.message(err));
        this.loading.set(false);
        this.code = '';
      },
    });
  }

  goToLogin(): void {
    this.router.navigateByUrl('/login');
  }

  private message(err: unknown): string {
    const e = err as { status?: number; error?: { message?: string; detail?: string } };
    if (e?.status === 401) {
      return this.i18n.t('mfa.invalidCode');
    }
    if (e?.status === 429) {
      return this.i18n.t('mfa.tooManyAttempts');
    }
    // 400 = expired/used/garbage link.
    return e?.error?.message || e?.error?.detail || this.i18n.t('reset.linkDead');
  }
}
