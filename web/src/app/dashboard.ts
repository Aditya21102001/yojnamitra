import { CommonModule } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Api } from './api';
import { Auth } from './auth';
import { HistoryItem, SavedScheme } from './models';

@Component({
  selector: 'app-dashboard',
  imports: [CommonModule, RouterLink],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.scss',
})
export class Dashboard implements OnInit {
  private readonly api = inject(Api);
  readonly auth = inject(Auth);

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
      next: () => this.saved.set(this.saved().filter((x) => x.id !== s.id)),
    });
  }
}
