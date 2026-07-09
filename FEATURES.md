# YojanaMitra — Features & Functionality

> What the application does, feature by feature, and where each one lives in the
> code. For *how the pieces fit together*, see [ARCHITECTURE.md](ARCHITECTURE.md).

---

## The problem

India runs thousands of central and state welfare schemes. Most eligible citizens
never claim their benefits — not because they are ineligible, but because they
never learn the scheme exists, or cannot tell from the legalese whether they
qualify.

YojanaMitra turns *"I am a 30-year-old woman farmer in Bihar earning ₹50,000 a
year and I need help buying seed"* into a ranked, explained list of the schemes
that person can actually claim.

---

## 1. Scheme matching

**The core feature.** Fill in whatever you know — every field is optional — and
get back the schemes you most likely qualify for.

| Input | Notes |
|---|---|
| Age, gender, state, occupation | All optional |
| Annual household income | In ₹ |
| Social category | General / SC / ST / OBC / Minority |
| Free text | "I want to start a small tailoring business and need a collateral-free loan" |

Each result shows:

- **Verdict badge** — `Likely eligible`, `Maybe eligible`, or `Not eligible`
- **Match score** — retrieval confidence as a percentage
- **Reason** — one plain-language line explaining the verdict
- **Benefit** and **How to apply**
- **Official site** link to the government portal

Results are ordered eligible → maybe → not_eligible, then by match score.

**No login required.** Anonymous users get the full matching experience.

*Code:* `web/src/app/match.ts` · `api/.../ApiController.match` ·
`api/.../AiService.match` · `ai/main.py:match`

---

## 2. Ask a question about a scheme

Expand any result and ask a follow-up in your own words — *"What documents do I
need?"* The LLM answers using only that scheme's data, so it cannot invent
requirements.

*Code:* `ai/main.py:chat` · `POST /api/chat`

---

## 3. Accounts and saved schemes

Sign up to keep a dashboard.

- **Save** any matched scheme with one tap
- **Dashboard** lists saved schemes and recent searches
- **Remove** a saved scheme
- **Match history** records every search you run while logged in

*Code:* `web/src/app/dashboard.ts` · `api/.../SavedController` ·
`api/.../MatchHistory`

---

## 4. Two-factor authentication (TOTP)

Opt-in, free, and works offline. No SMS charges and no phone number required.

**Enrolling** (from the dashboard):
1. Click *Enable two-factor*. The server generates a secret and renders a QR code.
2. Scan it with Google Authenticator, Authy, or any TOTP app. Can't scan? The
   same secret is printed as typeable text.
3. Enter the 6-digit code the app shows to confirm you can produce one.
4. **Save the 10 recovery codes.** They are shown exactly once.

**Logging in** afterwards becomes two steps: password, then a 6-digit code.

**Lost your phone?** Enter a recovery code instead of a TOTP code. Each works
once; the dashboard shows how many remain.

**Turning it off** requires both your password and a current code, so a hijacked
session cannot quietly weaken the account.

Security properties (all enforced server-side, all covered by tests):

- The token you get after the password step **cannot** be used as a session token
- A code is dead the moment it is used — no replay, even across the 30-second
  drift window
- 5 wrong codes → 15-minute lockout
- TOTP secrets are AES-GCM encrypted in the database
- Recovery codes are BCrypt-hashed; only their hashes are stored

*Code:* `api/.../auth/MfaService.java` · `api/.../security/TokenType.java` ·
`web/src/app/mfa-settings.ts` · `web/src/app/login.ts`

---

## 4b. Forgotten password

Give an email at sign-up (optional) and you can recover the account. Enter your
username *or* email on **Forgot your password?**, and a single-use link arrives
by mail. Open it, choose a new password, log in.

Four properties, all enforced server-side and covered by tests:

- **Accounts can't be enumerated.** `/auth/forgot` returns `204` identically for
  a real account, an unknown one, an account with no email on file, and a mail
  provider outage. Delivery failures are swallowed for the same reason.
- **The link is a credential.** 256 bits of `SecureRandom`, stored only as a
  SHA-256 hash, usable once, expiring in 15 minutes. Requesting a new link
  retires the previous one.
- **Reset can't bypass 2FA.** An account with MFA on must supply a code. And the
  reset endpoint issues *no session* — you log in afterwards, where MFA applies
  again. The property holds by construction, not by a check.
- **Old sessions die.** Changing the password stamps `passwordChangedAt`, and
  `JwtAuthFilter` refuses every token minted before it. A stolen token cannot
  outlive the reset meant to evict it.

An account created without an email simply cannot be recovered — there is no
channel to reach the user on. The sign-up form says so.

**Email is free.** The default provider (`log`) writes the link to the
application log — zero cost, works offline, right for dev. Set
`YOJANAMITRA_MAIL_PROVIDER=brevo` for real delivery (300 mails/day free).

*Code:* `api/.../auth/PasswordResetService.java` · `api/.../mail/` ·
`web/src/app/forgot-password.ts` · `web/src/app/reset-password.ts`

---

## 5. Hindi / English

A toggle in the navbar switches the entire interface between English and हिंदी,
and the choice persists across visits.

Two layers are translated:

- **UI strings** — from the `DICT` in `web/src/app/i18n.ts`
- **AI-generated text** — the reasons and chat answers are produced in the chosen
  language, because `lang` is passed through to the LLM prompt

The selected language is written to `<html lang>`, so screen readers pronounce
the page correctly.

Form option labels (states, social categories, gender) are translated too, while
the underlying `<option value>` stays English — it is sent to the API and matched
against the English scheme dataset.

*Code:* `web/src/app/i18n.ts` · `ai/main.py:_reason_prompt(lang=…)`

---

## 6. Accessibility

Built to WCAG 2.1 AA intent:

- **Keyboard** — skip link to main content, a visible focus ring on every
  interactive element, no keyboard traps
- **Screen readers** — `<main>`/`<nav>` landmarks, `aria-expanded` on the chat
  toggle, `aria-describedby` wiring field errors to their inputs, `role="alert"`
  on errors, a persistent live region for toasts, decorative emoji marked
  `aria-hidden`
- **Colour contrast** — all text meets AA against its background
- **Motion** — `prefers-reduced-motion` disables the shimmer and toast animations
- **Touch** — 44 px targets on small screens

---

## 7. Mobile

Responsive from 320 px up. Notably: form controls render at 16 px so iOS Safari
does not zoom in when a field is focused, long scheme names wrap instead of
forcing the page to scroll sideways, and the navbar wraps rather than overflowing.

---

## 8. Resilience

| Failure | Behaviour |
|---|---|
| LLM call fails | Retrieval-only results, verdict defaults to `maybe` |
| AI service unreachable | Gateway returns a clean `503` with a readable message |
| AI service cold (free tier) | Up to 90 s read timeout while the container wakes |
| Invalid or expired token | Request proceeds anonymously; protected routes return `401` |

---

## 9. Operational tooling

- **`ai/eval.py`** — runs labelled profiles through the same retrieval as
  `/match` and reports hit@1 / hit@3 / MRR. A fast, deterministic regression
  guard on match quality.
- **`ai/ingest.py`** — source → normalise → dedupe by id → rebuild the index.
  Grows the dataset from bundled batches or (best-effort) live government sources.
- **`GET /api/health`** — reports API status plus the AI service's own health,
  including which providers are configured and whether their keys are set.

---

## Feature status

| Feature | Status |
|---|---|
| Profile → matched schemes with reasons | ✅ |
| Inline per-scheme chat | ✅ |
| Accounts, saved schemes, history | ✅ |
| Hindi / English toggle (UI + AI output) | ✅ |
| TOTP two-factor authentication | ✅ |
| Accessibility & mobile responsiveness | ✅ |
| Postgres support | ✅ (via `DATABASE_URL`) |
| Live myscheme.gov.in ingestion at scale | ⏳ best-effort |
| Multi-instance deployment | ❌ MFA rate limiter is in-memory |
