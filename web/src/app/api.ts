import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ChatResponse, HistoryItem, MatchedScheme, MatchResponse, Profile, SavedScheme } from './models';
import { environment } from '../environments/environment';

/** Talks to the Spring Boot API only — never directly to the GenAI service. */
@Injectable({ providedIn: 'root' })
export class Api {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiBase;

  match(profile: Profile, lang = 'en', topK = 6): Observable<MatchResponse> {
    return this.http.post<MatchResponse>(`${this.base}/match`, { profile, topK, lang });
  }

  chat(schemeId: string, question: string, lang = 'en'): Observable<ChatResponse> {
    return this.http.post<ChatResponse>(`${this.base}/chat`, { schemeId, question, lang });
  }

  health(): Observable<unknown> {
    return this.http.get(`${this.base}/health`);
  }

  // ---- authenticated (token attached by the interceptor) ----

  saveScheme(s: MatchedScheme): Observable<SavedScheme> {
    return this.http.post<SavedScheme>(`${this.base}/saved`, {
      schemeId: s.id,
      name: s.name,
      category: s.category,
      verdict: s.verdict,
      reason: s.reason,
      applyUrl: s.apply_url,
    });
  }

  listSaved(): Observable<SavedScheme[]> {
    return this.http.get<SavedScheme[]>(`${this.base}/saved`);
  }

  deleteSaved(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/saved/${id}`);
  }

  getHistory(): Observable<HistoryItem[]> {
    return this.http.get<HistoryItem[]>(`${this.base}/history`);
  }
}
