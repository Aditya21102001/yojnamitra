import { Injectable, signal } from '@angular/core';

/** A tiny transient-notification service (e.g. "Saved ✓"). */
@Injectable({ providedIn: 'root' })
export class Toast {
  readonly message = signal<string | null>(null);
  private timer: ReturnType<typeof setTimeout> | undefined;

  show(message: string, ms = 2500): void {
    this.message.set(message);
    clearTimeout(this.timer);
    this.timer = setTimeout(() => this.message.set(null), ms);
  }
}
