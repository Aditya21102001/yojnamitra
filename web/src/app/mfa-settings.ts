import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Auth } from './auth';
import { I18n } from './i18n';
import { MfaSetup, MfaStatus } from './models';
import { Toast } from './toast';

/** Enrolment panel for the dashboard: turn TOTP on, show recovery codes, turn it off. */
@Component({
  selector: 'app-mfa-settings',
  imports: [FormsModule],
  templateUrl: './mfa-settings.html',
  styleUrl: './mfa-settings.scss',
})
export class MfaSettings implements OnInit {
  private readonly auth = inject(Auth);
  private readonly toast = inject(Toast);
  readonly i18n = inject(I18n);

  readonly status = signal<MfaStatus | null>(null);
  readonly setup = signal<MfaSetup | null>(null);
  /** Shown once, immediately after enabling. Never retrievable again. */
  readonly recoveryCodes = signal<string[] | null>(null);
  readonly busy = signal(false);
  readonly error = signal<string | null>(null);
  readonly disabling = signal(false);

  code = '';
  disablePassword = '';
  disableCode = '';

  ngOnInit(): void {
    this.auth.mfaStatus().subscribe({
      next: (s) => this.status.set(s),
      error: () => this.status.set({ enabled: false, recoveryCodesRemaining: 0 }),
    });
  }

  beginSetup(): void {
    this.busy.set(true);
    this.error.set(null);
    this.auth.mfaSetup().subscribe({
      next: (s) => {
        this.setup.set(s);
        this.busy.set(false);
      },
      error: (err) => {
        this.error.set(this.message(err));
        this.busy.set(false);
      },
    });
  }

  enable(): void {
    if (!this.code.trim()) {
      return;
    }
    this.busy.set(true);
    this.error.set(null);
    this.auth.mfaEnable(this.code.trim()).subscribe({
      next: (res) => {
        this.recoveryCodes.set(res.recoveryCodes);
        this.setup.set(null);
        this.code = '';
        this.busy.set(false);
        this.status.set({ enabled: true, recoveryCodesRemaining: res.recoveryCodes.length });
        this.toast.show(this.i18n.t('mfa.enabled'));
      },
      error: (err) => {
        this.error.set(this.message(err));
        this.busy.set(false);
        this.code = '';
      },
    });
  }

  disable(): void {
    if (!this.disablePassword || !this.disableCode.trim()) {
      return;
    }
    this.busy.set(true);
    this.error.set(null);
    this.auth.mfaDisable(this.disablePassword, this.disableCode.trim()).subscribe({
      next: () => {
        this.status.set({ enabled: false, recoveryCodesRemaining: 0 });
        this.recoveryCodes.set(null);
        this.disabling.set(false);
        this.disablePassword = '';
        this.disableCode = '';
        this.busy.set(false);
        this.toast.show(this.i18n.t('mfa.disabled'));
      },
      error: (err) => {
        this.error.set(this.message(err));
        this.busy.set(false);
        this.disableCode = '';
      },
    });
  }

  cancelSetup(): void {
    this.setup.set(null);
    this.code = '';
    this.error.set(null);
  }

  dismissRecoveryCodes(): void {
    this.recoveryCodes.set(null);
  }

  toggleDisableForm(): void {
    this.disabling.update((v) => !v);
    this.error.set(null);
  }

  copyRecoveryCodes(): void {
    const codes = this.recoveryCodes();
    if (!codes) {
      return;
    }
    navigator.clipboard.writeText(codes.join('\n')).then(
      () => this.toast.show(this.i18n.t('mfa.copied')),
      () => this.toast.show(this.i18n.t('mfa.copyFailed')),
    );
  }

  private message(err: unknown): string {
    const e = err as { status?: number; error?: { message?: string; detail?: string } };
    if (e?.status === 429) {
      return this.i18n.t('mfa.tooManyAttempts');
    }
    if (e?.status === 401) {
      return this.i18n.t('mfa.invalidCode');
    }
    return e?.error?.message || e?.error?.detail || this.i18n.t('error.generic');
  }
}
