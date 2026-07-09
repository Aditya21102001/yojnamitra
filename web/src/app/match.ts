import { CommonModule } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Api } from './api';
import { Auth } from './auth';
import { I18n } from './i18n';
import { MatchedScheme, MatchResponse, Profile, Verdict } from './models';
import { Toast } from './toast';

@Component({
  selector: 'app-match',
  imports: [CommonModule, FormsModule],
  templateUrl: './match.html',
  styleUrl: './match.scss',
})
export class Match {
  private readonly api = inject(Api);
  private readonly toast = inject(Toast);
  readonly auth = inject(Auth);
  readonly i18n = inject(I18n);

  form: Profile = {
    age: null,
    gender: '',
    state: '',
    occupation: '',
    annualIncome: null,
    category: '',
    description: '',
  };

  readonly genders = ['male', 'female', 'other'];
  readonly categories = ['General', 'SC', 'ST', 'OBC', 'Minority'];
  readonly states = [
    'Andhra Pradesh', 'Assam', 'Bihar', 'Chhattisgarh', 'Delhi', 'Gujarat',
    'Haryana', 'Himachal Pradesh', 'Jharkhand', 'Karnataka', 'Kerala',
    'Madhya Pradesh', 'Maharashtra', 'Odisha', 'Punjab', 'Rajasthan',
    'Tamil Nadu', 'Telangana', 'Uttar Pradesh', 'Uttarakhand', 'West Bengal',
  ];

  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly result = signal<MatchResponse | null>(null);
  readonly expandedId = signal<string | null>(null);

  // Inline "chat with this scheme"
  chatQuestion = '';
  readonly chatLoading = signal(false);
  readonly chatAnswer = signal<string | null>(null);

  // Saved-scheme state
  readonly savedIds = signal<Set<string>>(new Set());
  readonly savingId = signal<string | null>(null);

  submit(): void {
    this.loading.set(true);
    this.error.set(null);
    this.result.set(null);
    this.expandedId.set(null);

    this.api.match(this.cleanedProfile(), this.i18n.lang()).subscribe({
      next: (res) => {
        this.result.set(res);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(this.errorMessage(err));
        this.loading.set(false);
      },
    });
  }

  toggle(scheme: MatchedScheme): void {
    this.expandedId.set(this.expandedId() === scheme.id ? null : scheme.id);
    this.chatQuestion = '';
    this.chatAnswer.set(null);
  }

  ask(scheme: MatchedScheme): void {
    const q = this.chatQuestion.trim();
    if (!q) {
      return;
    }
    this.chatLoading.set(true);
    this.chatAnswer.set(null);
    this.api.chat(scheme.id, q, this.i18n.lang()).subscribe({
      next: (res) => {
        this.chatAnswer.set(res.answer);
        this.chatLoading.set(false);
      },
      error: (err) => {
        this.chatAnswer.set(this.errorMessage(err));
        this.chatLoading.set(false);
      },
    });
  }

  save(scheme: MatchedScheme): void {
    this.savingId.set(scheme.id);
    this.api.saveScheme(scheme).subscribe({
      next: () => {
        const next = new Set(this.savedIds());
        next.add(scheme.id);
        this.savedIds.set(next);
        this.savingId.set(null);
        this.toast.show(this.i18n.t('toast.saved'));
      },
      error: () => {
        this.savingId.set(null);
        this.toast.show(this.i18n.t('toast.saveError'));
      },
    });
  }

  isSaved(id: string): boolean {
    return this.savedIds().has(id);
  }

  verdictLabel(v: Verdict): string {
    return this.i18n.t('verdict.' + v);
  }

  private cleanedProfile(): Profile {
    const blankToNull = (v: string | null | undefined) => (v && v.trim() ? v.trim() : null);
    return {
      age: this.form.age ?? null,
      gender: blankToNull(this.form.gender),
      state: blankToNull(this.form.state),
      occupation: blankToNull(this.form.occupation),
      annualIncome: this.form.annualIncome ?? null,
      category: blankToNull(this.form.category),
      description: blankToNull(this.form.description),
    };
  }

  private errorMessage(err: unknown): string {
    const e = err as { error?: { detail?: string; message?: string }; message?: string };
    return e?.error?.detail || e?.error?.message || e?.message || this.i18n.t('error.generic');
  }
}
