import { CommonModule } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Api } from './api';
import { Auth } from './auth';
import { I18n } from './i18n';
import { HistoryItem, SavedScheme } from './models';
import { Toast } from './toast';

@Component({
  selector: 'app-dashboard',
  imports: [CommonModule, RouterLink],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.scss',
})
export class Dashboard implements OnInit {
  private readonly api = inject(Api);
  private readonly toast = inject(Toast);
  readonly auth = inject(Auth);
  readonly i18n = inject(I18n);

  readonly saved = signal<SavedScheme[]>([]);
  readonly history = signal<HistoryItem[]>([]);
  readonly loading = signal(true);

  ngOnInit(): void {
    this.api.listSaved().subscribe({
      next: (s) => this.saved.set(s),
      complete: () => this.loading.set(false),
      error: () => this.loading.set(false),
    });
    this.api.getHistory().subscribe({ next: (h) => this.history.set(h) });
  }

  remove(s: SavedScheme): void {
    this.api.deleteSaved(s.id).subscribe({
      next: () => {
        this.saved.set(this.saved().filter((x) => x.id !== s.id));
        this.toast.show(this.i18n.t('toast.removed'));
      },
    });
  }

  /** SavedScheme.verdict is a plain string, so fall back to the raw value
   *  rather than rendering a missing translation key. */
  verdictLabel(verdict: string): string {
    const key = 'verdict.' + verdict;
    const label = this.i18n.t(key);
    return label === key ? verdict : label;
  }
}
