import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { Auth } from './auth';

@Component({
  selector: 'app-register',
  imports: [FormsModule, RouterLink],
  templateUrl: './register.html',
})
export class Register {
  private readonly auth = inject(Auth);
  private readonly router = inject(Router);

  username = '';
  password = '';
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  submit(): void {
    if (this.username.trim().length < 3 || this.password.length < 6) {
      this.error.set('Username must be 3+ chars and password 6+ chars.');
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
      (e?.status === 409 ? 'That username is already taken' : 'Registration failed. Is the API running?')
    );
  }
}
