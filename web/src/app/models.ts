/** Shapes exchanged with the Spring Boot API (which passes the GenAI service
 *  response through unchanged, hence the snake_case fields on responses). */

export interface Profile {
  age?: number | null;
  gender?: string | null;
  state?: string | null;
  occupation?: string | null;
  annualIncome?: number | null;
  category?: string | null;
  description?: string | null;
}

export type Verdict = 'eligible' | 'maybe' | 'not_eligible';

export interface MatchedScheme {
  id: string;
  name: string;
  ministry: string;
  category: string;
  description: string;
  benefits: string;
  apply_url: string;
  verdict: Verdict;
  reason: string;
  how_to_apply: string;
  score: number;
}

export interface MatchResponse {
  query: string;
  count: number;
  schemes: MatchedScheme[];
}

export interface ChatResponse {
  scheme_id: string;
  answer: string;
}

export interface AuthResponse {
  token: string;
  username: string;
}

export interface SavedScheme {
  id: number;
  schemeId: string;
  name: string;
  category: string;
  verdict: string;
  reason: string;
  applyUrl: string;
  createdAt: string;
}

export interface HistoryItem {
  id: number;
  querySummary: string;
  schemeCount: number;
  topSchemeId: string;
  createdAt: string;
}
