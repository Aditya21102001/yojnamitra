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

/** `token` is null exactly when `mfaRequired` is true: the password step alone
 *  never yields a session for an account with a second factor. */
export interface AuthResponse {
  token: string | null;
  username: string;
  mfaRequired: boolean;
  mfaToken: string | null;
}

/** Enrolment material, returned once by POST /api/auth/mfa/setup. */
export interface MfaSetup {
  secret: string;
  qrDataUri: string;
  otpAuthUri: string;
}

export interface MfaStatus {
  enabled: boolean;
  recoveryCodesRemaining: number;
}

/** Result of GET /api/auth/reset/precheck — drives whether the page asks for a 2FA code. */
export interface ResetPrecheck {
  valid: boolean;
  mfaRequired: boolean;
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
