import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Auth } from './auth';
import { I18n } from './i18n';

@Component({
  selector: 'app-forgot-password',
  imports: [FormsModule, RouterLink],
  templateUrl: './forgot-password.html',
})
export class ForgotPassword {
  private readonly auth = inject(Auth);
  readonly i18n = inject(I18n);

  usernameOrEmail = '';
  readonly loading = signal(false);
  /** Once true, we show a neutral "check your email" message regardless of outcome. */
  readonly submitted = signal(false);

  submit(): void {
    if (!this.usernameOrEmail.trim()) {
      return;
    }
    this.loading.set(true);
    // The server answers identically for known and unknown accounts, so the UI
    // must not branch on success vs error either — both land on the same
    // confirmation. Anything else would leak which accounts exist.
    this.auth.forgotPassword(this.usernameOrEmail.trim()).subscribe({
      next: () => {
        this.loading.set(false);
        this.submitted.set(true);
      },
      error: () => {
        this.loading.set(false);
        this.submitted.set(true);
      },
    });
  }
}
