import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { Auth } from './auth';
import { I18n } from './i18n';

@Component({
  selector: 'app-register',
  imports: [FormsModule, RouterLink],
  templateUrl: './register.html',
})
export class Register {
  private readonly auth = inject(Auth);
  private readonly router = inject(Router);
  readonly i18n = inject(I18n);

  username = '';
  password = '';
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  submit(): void {
    if (this.username.trim().length < 3 || this.password.length < 6) {
      this.error.set(this.i18n.t('auth.lengthError'));
      return;
    }
    this.loading.set(true);
    this.error.set(null);
    this.auth.register(this.username.trim(), this.password).subscribe({
      next: () => this.router.navigateByUrl('/dashboard'),
      error: (err) => {
        this.error.set(this.message(err));
        this.loading.set(false);
      },
    });
  }

  private message(err: unknown): string {
    const e = err as { status?: number; error?: { message?: string; detail?: string } };
    return (
      e?.error?.message ||
      e?.error?.detail ||
      this.i18n.t(e?.status === 409 ? 'auth.taken' : 'auth.registerFailed')
    );
  }
}
