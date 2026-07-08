import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { Auth } from './auth';

@Component({
  selector: 'app-login',
  imports: [FormsModule, RouterLink],
  templateUrl: './login.html',
})
export class Login {
  private readonly auth = inject(Auth);
  private readonly router = inject(Router);

  username = '';
  password = '';
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  submit(): void {
    if (!this.username.trim() || !this.password) {
      return;
    }
    this.loading.set(true);
    this.error.set(null);
    this.auth.login(this.username.trim(), this.password).subscribe({
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
      (e?.status === 401 ? 'Invalid username or password' : 'Login failed. Is the API running?')
    );
  }
}
