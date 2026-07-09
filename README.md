# YojanaMitra 🏛️

**Find the government welfare schemes you actually qualify for — in plain language.**

India has thousands of central & state welfare schemes, but discovery is broken:
most eligible citizens never claim benefits because they don't know the schemes
exist or can't tell if they qualify. **YojanaMitra** lets a citizen describe their
situation in plain words and uses a local LLM to match them to eligible schemes,
explain *why* they qualify, and tell them how to apply.

> Portfolio project by Aditya Yadav — a fully open-source, free-to-run,
> **Angular + Spring Boot + Python/GenAI** application.

---

## Architecture (3 services)

```
┌─────────────┐   REST/JSON    ┌──────────────────┐  internal REST  ┌────────────────────┐
│  Angular    │ ─────────────► │  Spring Boot API │ ──────────────► │  Python / FastAPI  │
│  (web/)     │ ◄───────────── │  (api/, Java 17) │ ◄────────────── │   GenAI (ai/)      │
└─────────────┘                └────────┬─────────┘                 └─────────┬──────────┘
                                        │ JPA                                 │
                                  ┌─────▼─────┐                        ┌──────▼──────┐
                                  │  H2 / PG  │                        │   Ollama    │  in-memory
                                  │  (history)│                        │ llama3.2:1b │  cosine idx
                                  └───────────┘                        └─────────────┘
```

| Layer        | Tech (all free / open-source)                              |
| ------------ | ---------------------------------------------------------- |
| Frontend     | Angular 21 (zoneless, signals) + SCSS                       |
| API / gateway| Spring Boot 3.3, Java 17, Spring Data JPA, H2 (→ Postgres)  |
| GenAI service| Python 3.13, FastAPI (in-memory cosine retrieval)          |
| LLM + embeds | Pluggable — Ollama locally, Groq + Jina in the cloud        |
| Auth         | Spring Security + JWT (jjwt), BCrypt passwords, TOTP 2FA    |
| Data         | Curated Indian government schemes (`ai/data/schemes.json`)  |

> The chat model runs on **CPU** (`num_gpu:0` in `ai/ollama_client.py`) so it works
> on machines with a small or no GPU; embeddings use the GPU when available.
> A free 512 MB cloud instance can't run Ollama, so `ai/Dockerfile` switches to
> hosted Groq + Jina via `LLM_PROVIDER` / `EMBED_PROVIDER` — see `ai/providers.py`.

## Documentation

| Doc | Read it for |
| --- | ----------- |
| [ONBOARDING.md](ONBOARDING.md) | **Start here.** Vocabulary, repo map, running it, first change |
| [ARCHITECTURE.md](ARCHITECTURE.md) | How the three services fit together; the auth & MFA design |
| [FEATURES.md](FEATURES.md) | Every feature and where it lives in the code |
| [DEPLOY.md](DEPLOY.md) | Deploying to Render / Neon |

---

## Quick start

Run each service in its own terminal. Order: **Ollama → ai → api → web**.

### 0. Ollama (local models)
```bash
ollama pull llama3.2:1b
ollama pull nomic-embed-text
ollama serve            # usually already running as a service
```

### 1. Python GenAI service (`ai/`, port 8000)
```bash
cd ai
python -m venv .venv
.venv\Scripts\activate          # Windows;  source .venv/bin/activate on macOS/Linux
pip install -r requirements.txt
python seed.py                  # build the vector index from schemes.json
uvicorn main:app --reload --port 8000
```

### 2. Spring Boot API (`api/`, port 8080)
```bash
cd api
mvn spring-boot:run
```

### 3. Angular web (`web/`, port 4200)
```bash
cd web
npm install                     # if not already done
npm start
```

Open http://localhost:4200. You can match anonymously; **sign up / log in** to save
schemes and see your dashboard.

---

## API

All endpoints are under `http://localhost:8080/api`. Browsing and matching are
public; **saving schemes and your dashboard require a Bearer token**.

| Method | Endpoint            | Auth | Purpose                                                        |
| ------ | ------------------- | ---- | ------------------------------------------------------------- |
| POST   | `/auth/register`    | —    | Create an account → `{ token, username }`                     |
| POST   | `/auth/login`       | —    | Log in → a session, **or** an MFA challenge (see below)       |
| POST   | `/auth/mfa/verify`  | —    | Exchange `{ mfaToken, code }` for a session                   |
| POST   | `/auth/forgot`      | —    | Request a reset link. **Always `204`**, so accounts can't be enumerated |
| GET    | `/auth/reset/precheck?token=` | — | `{ valid, mfaRequired }` — is the link alive, does it need a 2FA code |
| POST   | `/auth/reset`       | —    | `{ token, newPassword, code? }` → `204`. Issues **no session** by design |
| GET    | `/auth/me`          | —    | Current user (from the token, if present)                     |
| GET    | `/auth/mfa/status`  | 🔒   | `{ enabled, recoveryCodesRemaining }`                         |
| POST   | `/auth/mfa/setup`   | 🔒   | Begin enrolment → `{ secret, qrDataUri, otpAuthUri }`         |
| POST   | `/auth/mfa/enable`  | 🔒   | Confirm a code → `{ recoveryCodes }` (shown once)             |
| POST   | `/auth/mfa/disable` | 🔒   | Requires `{ password, code }`                                 |
| GET    | `/health`           | —    | API + GenAI provider status                                   |
| GET    | `/schemes`          | —    | The full curated scheme list                                  |
| POST   | `/match`            | opt. | Profile → ranked, explained schemes (saved to your history if logged in) |
| POST   | `/chat`             | —    | Ask a question about one scheme                               |
| POST   | `/saved`            | 🔒   | Save a scheme                                                 |
| GET    | `/saved`            | 🔒   | Your saved schemes                                            |
| DELETE | `/saved/{id}`       | 🔒   | Remove a saved scheme                                         |
| GET    | `/history`          | 🔒   | Your recent matches                                           |

**Auth flow.** `register` and `login` return a 24-hour **JWT** (HMAC-SHA; the key
length picks HS256/HS384/HS512). Send it as `Authorization: Bearer <token>` on 🔒
calls. The Angular app stores it in `localStorage`, attaches it via an HTTP
interceptor, and guards `/dashboard` with a route guard.

**With two-factor enabled**, `login` returns no session — only a short-lived
challenge:

```jsonc
// POST /auth/login  (MFA on)
{ "token": null, "mfaRequired": true, "mfaToken": "eyJ…", "username": "demo" }

// POST /auth/mfa/verify  { "mfaToken": "eyJ…", "code": "123456" }
{ "token": "eyJ…", "mfaRequired": false, "mfaToken": null, "username": "demo" }
```

The challenge token carries a `typ: MFA_CHALLENGE` claim and is rejected by the
auth filter, so it can never be used as a session token. See
[ARCHITECTURE.md §4](ARCHITECTURE.md) for the full design.

```bash
# register, capture the token, and save a scheme
TOKEN=$(curl -s -X POST localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"demo","password":"demo123"}' | jq -r .token)

curl -X POST localhost:8080/api/saved -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"schemeId":"pm-kisan","name":"PM-KISAN","verdict":"eligible"}'
```

### Configuration (env vars)

| Variable                  | Default            | Used by                              |
| ------------------------- | ------------------ | ------------------------------------ |
| `DATABASE_URL`            | —                  | API — Postgres connection string; auto-activates the postgres profile |
| `YOJANAMITRA_JWT_SECRET`  | dev secret         | API — JWT signing key (set in prod)  |
| `YOJANAMITRA_MFA_ENC_KEY` | dev key            | API — encrypts TOTP secrets at rest. **Rotating it invalidates every MFA enrolment** |
| `YOJANAMITRA_AI_BASE_URL` | `http://localhost:8000` | API — where the GenAI service lives |
| `YOJANAMITRA_CORS_ALLOWED_ORIGINS` | `http://localhost:4200` | API — comma-separated origins |
| `YOJANAMITRA_WEB_BASE_URL` | `http://localhost:4200` | API — origin baked into password-reset links |
| `YOJANAMITRA_MAIL_PROVIDER` | `log`            | API — `log` prints reset links to the log; `brevo` actually sends them |
| `BREVO_API_KEY` / `YOJANAMITRA_MAIL_FROM` | — | API — required when the provider is `brevo` |
| `DB_ENGINE`               | `h2`               | API — set `postgres` to use Postgres (when `DATABASE_URL` is unset) |
| `DB_HOST` / `DB_PORT` / `DB_NAME` / `DB_USER` / `DB_PASSWORD` | localhost:5432/yojanamitra | API — Postgres connection |
| `LLM_PROVIDER`            | `ollama`           | GenAI — `ollama` or `groq`           |
| `EMBED_PROVIDER`          | `ollama`           | GenAI — `ollama`, `jina`, or `fastembed` |
| `GROQ_API_KEY` / `JINA_API_KEY` | —            | GenAI — required by the cloud providers |
| `CHAT_MODEL`              | `llama3.2:1b`      | GenAI — Ollama chat model            |
| `EMBED_MODEL`             | `nomic-embed-text` | GenAI — Ollama embedding model       |
| `OLLAMA_HOST`             | `http://localhost:11434` | GenAI                          |

---

## Data & database (Phase 3)

### Ingest more schemes
The dataset lives in `ai/data/schemes.json`. Grow it with the ingestion pipeline
(source → normalize → merge/dedupe by id → rebuild the vector index):

```bash
cd ai
python ingest.py --source extra       # add the bundled extra batch (offline, +8 schemes)
python ingest.py --source curated     # just re-index the current dataset
# live sources are best-effort and need config:
DATA_GOV_API_KEY=... DATA_GOV_RESOURCE_ID=... python ingest.py --source data_gov
MYSCHEME_ENABLE=1 python ingest.py --source myscheme
```
If the GenAI service is running, `POST /admin/seed` afterwards to refresh its
in-memory index (or just restart it).

### Evaluate match quality
`ai/eval.py` runs labeled profiles through the same retrieval as `/match` and
reports hit@1 / hit@3 / MRR — a fast, deterministic regression guard:
```bash
cd ai && python eval.py     # current dataset: hit@1 100%, hit@3 100%, MRR 1.000
```

### Use PostgreSQL instead of H2
The API defaults to in-memory H2. Set `DB_ENGINE=postgres` to switch:

```bash
# Option A — Docker:
docker compose up -d                  # starts Postgres 16 with db/user/pass = yojanamitra/postgres/postgres

# Option B — an existing local Postgres: create the db, then point the API at it
createdb yojanamitra
DB_ENGINE=postgres DB_USER=postgres DB_PASSWORD=yourpass mvn -f api spring-boot:run

# Option C — a cloud Postgres (Neon / Supabase / Render) via ONE connection string:
DATABASE_URL="postgresql://user:pass@ep-xxx.neon.tech/neondb?sslmode=require" mvn -f api spring-boot:run
```
JPA creates the tables automatically (`ddl-auto: update`). `DATABASE_URL` (the
12-factor style Neon/Render/Railway give you) auto-activates the Postgres profile,
forces SSL, and drops params the JDBC driver rejects (e.g. `channel_binding`) —
verified live against Neon.

---

## Roadmap

- **Phase 1 (MVP)** — profile → match → results + inline chat. ✅ done
- **Phase 2** — JWT auth, saved-matches dashboard. ✅ done
- **Phase 3** — Postgres support, ingestion pipeline (`ai/ingest.py`), **Hindi/EN
  language toggle** (AI reasons in the chosen language), an eval harness
  (`ai/eval.py`), and deploy scaffolding (Dockerfiles, [DEPLOY.md](DEPLOY.md),
  [render.yaml](render.yaml)). ✅ largely done
- **Phase 4** — **TOTP two-factor auth** (opt-in, free, with recovery codes),
  WCAG 2.1 AA accessibility pass, and mobile responsiveness. ✅ done
- **Next** — live myscheme.gov.in ingestion at scale; a hosted-LLM deploy path
  (free tiers can't run Ollama — see DEPLOY.md); move the MFA rate limiter out of
  process so the API can scale past one instance.
