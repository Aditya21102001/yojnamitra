# YojanaMitra — Architecture

> Audience: an engineer who has never seen this repo. Read this top-to-bottom once
> and you should be able to find any piece of the system.
> See also: [FEATURES.md](FEATURES.md) (what it does) and
> [ONBOARDING.md](ONBOARDING.md) (how to run it and make your first change).

---

## 1. What the system does

A citizen describes their situation — age, gender, state, occupation, income,
social category, plus free text. The system returns the government welfare
schemes they are most likely eligible for, each with:

- a **verdict** (`eligible` / `maybe` / `not_eligible`),
- a plain-language **reason** for that verdict,
- a **how-to-apply** line and a link to the official portal.

They can then ask follow-up questions about any single scheme.

**The AI never invents schemes.** Candidates always come from a curated,
auditable dataset (`ai/data/schemes.json`) via semantic retrieval. The LLM only
*reasons about eligibility* and *phrases things simply*. For a public-benefits
tool this is the whole ballgame — a hallucinated scheme would send a poor person
to a government office for something that does not exist.

---

## 2. Three services, and why the split

```
   Angular SPA                 Spring Boot API                 Python FastAPI
   web/  :4200                 api/  :8080                     ai/  :8000
 ┌────────────────┐         ┌──────────────────────┐        ┌──────────────────────┐
 │ profile form   │  REST   │ AuthController       │  REST  │ /match  /chat        │
 │ results        │ ──────► │ ApiController        │ ─────► │ /schemes  /health    │
 │ inline chat    │  JSON   │ SavedController      │  JSON  │                      │
 │ dashboard      │ ◄────── │                      │ ◄───── │ profile → query text │
 │ MFA enrolment  │         │ Spring Security+JWT  │        │ embed → cosine search│
 └────────────────┘         │ JPA                  │        │ LLM eligibility call │
                            └──────────┬───────────┘        └──────────┬───────────┘
                                       │                               │
                              ┌────────▼────────┐          ┌───────────▼───────────┐
                              │  H2 (dev)       │          │  providers.py         │
                              │  Postgres (prod)│          │  LLM:   ollama | groq │
                              │  users, saved,  │          │  embed: ollama | jina │
                              │  history, MFA   │          │          | fastembed  │
                              └─────────────────┘          └───────────────────────┘
```

| Service | Responsibility | Why its own tier |
|---|---|---|
| **web/** | UI, forms, presentation, i18n, accessibility | Standard SPA boundary |
| **api/** | System of record. Validation, authentication, authorization, orchestration, history | The backend an organisation owns and audits |
| **ai/** | Embeddings, retrieval, LLM reasoning | GenAI concerns isolated in Python; swappable without touching business logic |

**Angular talks only to Spring Boot. Spring Boot is the only caller of the Python
service.** That single seam is load-bearing: authentication, rate limiting,
CORS, and match history all live in exactly one place, and the AI service stays
a stateless, replaceable engine with no notion of users.

The AI service is **not** exposed to the internet by design — it has no auth of
its own. Anything that must be trusted happens in `api/`.

---

## 3. The match pipeline (retrieval-augmented reasoning)

`POST /api/match` → `AiService.match()` → `POST /match` on the AI service.

1. **Profile → query string.** `Profile.to_query()` (`ai/models.py`) flattens the
   form into one natural-language sentence.
2. **Embed.** The sentence becomes a vector via `providers.embed()`.
3. **Retrieve.** `store.search()` ranks every scheme by cosine similarity and
   returns the top-K, each with a score in `[0, 1]`. Schemes were indexed from
   name + description + eligibility + benefits + tags.
4. **Reason.** All candidates plus the profile go to the LLM in a **single**
   JSON-mode call. Per scheme, the model returns a verdict, a one-line reason,
   and a how-to-apply line, grounded strictly in the supplied eligibility text.
5. **Rank & return.** Ordered `eligible` → `maybe` → `not_eligible`, then by
   retrieval score.

**Graceful degradation.** If the LLM call fails, the service returns
retrieval-only results with verdict `maybe` rather than erroring. If the whole AI
service is unreachable, `AiService` converts the failure into a clean `503` with
a helpful message.

### Lazy seeding — and the cold-start trap

The vector index (`ai/data/index.json`) is built on first use, not at boot
(`store.ensure_seeded()`). This is deliberate: seeding at boot delayed the port
binding and spiked memory on a 512 MB instance.

The cost lands on the **first `/match` after a cold start**. On a free-tier host
that sleeps after ~15 minutes idle, that first request pays container wake +
index build + LLM call, which can exceed the API's 90-second read timeout and
surface to the user as a `503`. Keeping the AI service warm (a periodic
`/health` ping) is the practical fix.

---

## 4. Authentication and authorization

### 4.1 Password login

`AuthenticationManager` verifies the username/password against BCrypt-hashed
credentials loaded by `AppUserDetailsService`. On success `JwtService` issues a
token.

The signing algorithm is chosen by key length: `Keys.hmacShaKeyFor()` yields
**HS256** for a 32–47 byte secret, **HS384** for 48–63, **HS512** for 64+. The
default dev secret is 62 characters, so tokens are signed **HS384**.

### 4.2 Two-factor authentication (TOTP)

MFA is **opt-in** and free — RFC 6238 time-based codes from any authenticator
app (Google Authenticator, Authy). No SMS, no email, no third-party service.

```
POST /api/auth/login
      │
      ├── MFA off ──────────────────► { token, username, mfaRequired: false }
      │
      └── MFA on  ──────────────────► { token: null, mfaRequired: true, mfaToken }
                                                                          │
                            POST /api/auth/mfa/verify { mfaToken, code } ──┘
                                          │
                                          ▼
                                { token, username, mfaRequired: false }
```

**The critical invariant: a challenge token is not a session token.**

Both are signed with the same key and carry the same subject. Without a
distinguishing claim, anyone who knew only the password could send the challenge
token as `Authorization: Bearer …` and walk straight past the second factor.
So every token carries a `typ` claim (`ACCESS` or `MFA_CHALLENGE`; see
`TokenType`), `JwtService.extractUsername(token, expectedType)` demands the
right one, and `JwtAuthFilter` accepts **only** `ACCESS`.

`JwtServiceTest` and `MfaFlowTest.mfaChallengeTokenCannotAuthenticateProtectedEndpoints`
exist to make that regression loud.

Supporting properties, all in `MfaService`:

| Property | How |
|---|---|
| Secrets safe at rest | AES-GCM encrypted via `SecretCipher`, keyed by `YOJANAMITRA_MFA_ENC_KEY` |
| No code replay | The **exact time-step the code matched** is burned; later codes must belong to a strictly newer step |
| Clock drift tolerated | ±1 step (30 s) either side of now |
| Brute force throttled | 5 failures → 15-minute lockout, per username |
| Lost phone | 10 single-use recovery codes, BCrypt-hashed, shown once |
| Turning MFA off | Requires **both** the password and a current code |

> **Why "burn the matched step" and not "burn the current step"?**
> Drift tolerance accepts a code from step `S` while the clock reads `S+1`. If
> the guard only recorded `now`, a code spent at `S` (setting `last = S`) would
> still pass at `S+1`, because `S+1 > S`. That is a real ~30-second replay
> window. `TotpReplayTest` drives a fake clock across the boundary to prove it
> stays closed.

### 4.3 Route protection

`SecurityConfig` is **order-sensitive** — first match wins:

```java
.requestMatchers("/api/auth/mfa/status", "/api/auth/mfa/setup",
                 "/api/auth/mfa/enable", "/api/auth/mfa/disable").authenticated()
.requestMatchers("/api/auth/**", "/api/health", "/api/schemes",
                 "/api/match", "/api/chat").permitAll()
.requestMatchers("/api/saved/**", "/api/history").authenticated()
```

The MFA management endpoints **must** be listed before the `/api/auth/**`
wildcard. Under it they would inherit `permitAll()` and let an anonymous caller
mint or strip another account's second factor.

### 4.4 On the client

- `auth-interceptor.ts` attaches `Authorization: Bearer <token>` to every request.
- `auth-guard.ts` blocks `/dashboard` for logged-out users.
- `auth.ts` persists the token in `localStorage` and, crucially, **does not
  create a session when `mfaRequired` is true**.

---

## 5. Data

### Database (owned by `api/`)

| Table | Entity | Holds |
|---|---|---|
| `app_user` | `AppUser` | username, BCrypt password, MFA secret + enabled flag + last TOTP step |
| `saved_scheme` | `SavedScheme` | schemes a user bookmarked |
| `match_history` | `MatchHistory` | one row per match run |
| `mfa_recovery_code` | `MfaRecoveryCode` | BCrypt hashes, `usedAt` marks a code spent |

Schema is created by Hibernate (`ddl-auto: update`). There are no migrations —
adding a column to an entity is enough.

**Datasource selection** happens in `ApiApplication.main()` *before* Spring
starts. If `DATABASE_URL` is set (the 12-factor string Neon/Render/Railway
give you), `applyDatabaseUrl()` parses it, sets `spring.datasource.*` and forces
`spring.profiles.active=postgres` as **system properties** — which outrank
`application.yml`. Absent that, `spring.profiles.active` falls back to
`${DB_ENGINE:h2}` and you get in-memory H2.

> **In-memory H2 loses everything on restart** — users, saved schemes, MFA
> enrolments. Fine for local dev; never what you want in production. Confirm the
> log line `[YojanaMitra] DATABASE_URL detected -> …` on boot.

### Scheme dataset (owned by `ai/`)

`ai/data/schemes.json` is the single source of truth: real central-government
schemes with `eligibility`, `benefits`, `apply_url`, `tags`. `ai/data/index.json`
is the derived vector index — regenerate with `python seed.py` or
`POST /admin/seed`.

---

## 6. Pluggable AI providers

`ai/providers.py` is a thin abstraction so the same code runs locally on free
local models or in the cloud on free hosted APIs. Callers use `providers.embed`,
`generate`, `generate_json`, `health` and catch `ProviderError`.

| Env var | Values | Default | Notes |
|---|---|---|---|
| `LLM_PROVIDER` | `ollama`, `groq` | `ollama` | Cloud deploy sets `groq` |
| `EMBED_PROVIDER` | `ollama`, `jina`, `fastembed` | `ollama` | Cloud deploy sets `jina` |

`ai/Dockerfile` sets `groq` + `jina` because a free 512 MB instance cannot run
Ollama. Locally the defaults keep everything on your machine, private and
offline. `fastembed` is the middle ground: local CPU embeddings, ~400 MB RAM, no
embedding API key.

---

## 7. Frontend architecture

Angular 21, **zoneless** (no `zone.js` dependency at all), standalone components,
signal-based state throughout (`signal()`, `computed()`).

| File | Role |
|---|---|
| `app.routes.ts` | 4 routes; `/dashboard` behind `authGuard` |
| `auth.ts` | Token + username signals, login/register/MFA calls |
| `api.ts` | Every call to the Spring API |
| `i18n.ts` | English/Hindi dictionary, `lang` signal, syncs `<html lang>` |
| `match.ts` | The main form and results |
| `mfa-settings.ts` | Enrol / confirm / disable, recovery codes |
| `toast.ts` | Transient notifications |

**i18n.** A single `DICT` keyed by dotted strings; `i18n.t(key, fallback?)`
returns the string for the active language. Keys built from data (e.g.
`'state.' + name`) pass a fallback so a missing translation renders the English
name rather than a raw key. An `effect()` keeps `<html lang>` in sync — screen
readers pick pronunciation from it, so rendering Devanagari under `lang="en"`
makes the page unintelligible aloud.

Decorative glyphs (`☆ ✓ ↗`, emoji) live in the **templates**, not the
dictionary, so they can be `aria-hidden` instead of being read out.

---

## 8. Cross-cutting design decisions

- **Retrieval-grounded, not generative discovery.** The LLM cannot hallucinate a
  scheme that does not exist.
- **JSON-mode reasoning call.** Reliable parsing, no brittle regex.
- **Graceful degradation.** LLM down ⇒ retrieval-only results. AI service down ⇒
  a clean `503` from the gateway.
- **camelCase ↔ snake_case translation at the gateway.** Java and Python each
  stay idiomatic; `AiService` owns the mapping.
- **One seam to the AI.** Auth, history, and rate limiting have exactly one place
  to live.

---

## 9. Known limitations

| Limitation | Consequence |
|---|---|
| MFA rate limiter is an in-memory map | Resets on restart; incorrect if the API is ever scaled past one instance |
| No database migrations (`ddl-auto: update`) | Fine for a small app; a destructive schema change has no rollback |
| `YOJANAMITRA_MFA_ENC_KEY` rotation | Makes every existing MFA enrolment undecryptable |
| Free-tier cold starts | First `/match` after idle can exceed the 90 s read timeout |
| Adding the `typ` JWT claim | Tokens issued before it are rejected; users re-authenticate once |
