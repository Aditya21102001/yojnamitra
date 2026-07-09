# Onboarding — start here

> For someone new to this codebase, and possibly new to one of its three stacks.
> By the end you will have the app running locally, know what every folder is
> for, and have made a change with confidence.
>
> Companion docs: [ARCHITECTURE.md](ARCHITECTURE.md) (how it fits together),
> [FEATURES.md](FEATURES.md) (what it does).

---

## 1. The one-paragraph explanation

A citizen types their situation into a web page. Angular sends it to a Spring
Boot API. Spring forwards it to a Python service, which finds the most similar
government schemes in a small curated dataset and asks an LLM to judge whether
this person qualifies for each one. The answer travels back the same way.
Angular shows a list of schemes with a verdict, a reason, and a link.

Everything else in this repo — accounts, saved schemes, two-factor auth, Hindi
translation — is built around that one flow.

---

## 2. Vocabulary

If any of these are unfamiliar, read this table before the code.

| Term | What it means here |
|---|---|
| **Scheme** | A government welfare programme (PM-KISAN, Ayushman Bharat, …) |
| **Embedding** | A list of numbers representing text's meaning. Similar meanings → similar numbers |
| **Cosine similarity** | How close two embeddings point in the same direction. `1.0` = identical meaning, `0` = unrelated |
| **Vector search / retrieval** | Embed the user's query, compare against every scheme's embedding, return the closest |
| **RAG** | Retrieval-Augmented Generation. Fetch real facts first, *then* let the LLM talk — so it can't make things up |
| **LLM** | Large Language Model. Here it only *judges and explains*, never *discovers* |
| **JWT** | A signed string proving "I am user X". Sent as `Authorization: Bearer <token>` |
| **BCrypt** | A deliberately slow password hash. Slow = expensive to brute-force |
| **TOTP** | Time-based One-Time Password. The 6-digit code your authenticator app shows |
| **Signal** (Angular) | A reactive value. Read it as `count()`, set it as `count.set(5)` |
| **Zoneless** | This app has no `zone.js`. Signals drive change detection instead |

---

## 3. Repository map

```
yojanamitra/
├── web/          Angular 21 SPA          → what the user sees
├── api/          Spring Boot 3.3 (Java 17) → auth, database, gateway
├── ai/           Python 3.13 + FastAPI   → embeddings, retrieval, LLM
├── render.yaml   Deployment blueprint
└── *.md          Documentation (you are here)
```

### `web/src/app/` — every file, one line each

| File | What it does |
|---|---|
| `app.ts` / `app.html` | Shell: navbar, router outlet, toast |
| `app.routes.ts` | 4 routes. `/dashboard` is guarded |
| `app.config.ts` | Bootstrap: router, HTTP client, interceptor |
| `match.ts` | The main page — profile form and results |
| `dashboard.ts` | Saved schemes, search history |
| `login.ts` / `register.ts` | Auth screens; `login.ts` also handles the MFA code step |
| `mfa-settings.ts` | Enrol in / disable two-factor |
| `auth.ts` | Token state; all `/auth/**` calls |
| `api.ts` | All other API calls |
| `auth-interceptor.ts` | Attaches the `Bearer` token to every request |
| `auth-guard.ts` | Redirects logged-out users away from `/dashboard` |
| `i18n.ts` | English/Hindi dictionary and language signal |
| `toast.ts` | Transient "Saved ✓" notifications |
| `models.ts` | TypeScript shapes of the API's JSON |

### `api/src/main/java/com/yojanamitra/api/`

| Package | What it does |
|---|---|
| `auth/` | Register, login, MFA endpoints. `MfaService` is the interesting one |
| `security/` | `JwtService` (issue/verify), `JwtAuthFilter` (read the header), `SecurityConfig` (who can call what), `SecretCipher` (encrypt TOTP secrets) |
| `user/` | `AppUser` entity, recovery codes, `UserDetailsService` |
| `web/` | `ApiController` — `/health`, `/match`, `/chat`, `/schemes` |
| `saved/` | Save and list bookmarked schemes |
| `history/` | One row per match run |
| `service/AiService` | The **only** code that talks to the Python service |
| `config/WebConfig` | The HTTP client pointed at the AI service |
| `ApiApplication` | Entry point. Parses `DATABASE_URL` before Spring boots |

### `ai/`

| File | What it does |
|---|---|
| `main.py` | FastAPI routes: `/match`, `/chat`, `/schemes`, `/health` |
| `store.py` | The vector index. `build_index()`, `search()` |
| `providers.py` | Swap Ollama ↔ Groq/Jina by env var |
| `models.py` | Pydantic request/response shapes |
| `seed.py` | Build the index from `data/schemes.json` |
| `eval.py` | Measure match quality (hit@1, hit@3, MRR) |
| `ingest.py` | Add more schemes to the dataset |
| `data/schemes.json` | **Source of truth.** The curated scheme list |
| `data/index.json` | Derived embeddings. Safe to delete and rebuild |

---

## 4. Run it locally

You do **not** need all three services for every task. Pick the smallest set:

| Working on… | Run |
|---|---|
| UI styling, i18n, accessibility | `web` only |
| Login, MFA, saved schemes | `web` + `api` |
| Matching, chat, prompts | all three |

### Prerequisites

Java 17+, Node 20+, Python 3.11+, Maven. For local LLM: [Ollama](https://ollama.com).

### 0. Ollama — only if you want the AI features locally

```bash
ollama pull llama3.2:1b
ollama pull nomic-embed-text
ollama serve
```

> No GPU? It runs on CPU. Don't want Ollama at all? Set `EMBED_PROVIDER=fastembed`
> (local, no key) or use free Groq + Jina API keys — see `ai/providers.py`.

### 1. AI service — port 8000

```bash
cd ai
python -m venv .venv
.venv\Scripts\activate          # Windows
# source .venv/bin/activate     # macOS / Linux
pip install -r requirements.txt
python seed.py                  # build the vector index
uvicorn main:app --reload --port 8000
```

### 2. API — port 8080

```bash
cd api
mvn spring-boot:run
```

Uses in-memory H2 by default. Zero setup; **all data is wiped when you stop it.**

### 3. Web — port 4200

```bash
cd web
npm install
npm start
```

Open <http://localhost:4200>.

### If the JVM won't start

On a memory-constrained machine you may see
`The paging file is too small for this operation to complete`. The JVM defaults
its max heap to ¼ of physical RAM and reserves virtual address space to match.
Bound it:

```bash
MAVEN_OPTS="-Xmx400m -XX:+UseSerialGC" mvn spring-boot:run
```

---

## 5. Try the API without a browser

```bash
# Register and capture a token
TOKEN=$(curl -s -X POST localhost:8080/api/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"username":"demo","password":"demo123"}' | jq -r .token)

# Everything is healthy?
curl -s localhost:8080/api/health | jq

# Match (works without a token too)
curl -s -X POST localhost:8080/api/match \
  -H 'Content-Type: application/json' \
  -d '{"profile":{"age":30,"gender":"female","state":"Bihar",
                  "occupation":"farmer","annualIncome":50000,
                  "category":"OBC","description":"need farming support"},
       "topK":3,"lang":"en"}' | jq '.schemes[].name'

# A protected route needs the token
curl -s localhost:8080/api/history -H "Authorization: Bearer $TOKEN" | jq
```

---

## 6. Reading the code in the right order

Follow one request end-to-end. This is the fastest way to hold the system in
your head.

1. `web/src/app/match.ts` → `submit()` — the button handler
2. `web/src/app/api.ts` → `match()` — the HTTP call
3. `api/.../web/ApiController.java` → `match()` — arrives in Java
4. `api/.../service/AiService.java` → `match()` — camelCase → snake_case, forward
5. `ai/main.py` → `match()` — retrieval + LLM
6. `ai/store.py` → `search()` — the actual cosine similarity
7. Back up the stack. `match.ts` sets `result.set(res)`; the template re-renders.

Then read `MfaService.java` — it is the densest and most interesting file in the
repo, and its comments explain *why*, not *what*.

---

## 7. Tests

```bash
cd api && mvn test          # 15 tests: JWT typing, MFA flow, TOTP replay
cd web && npx ng build      # type-checks templates and TypeScript
cd ai  && python eval.py    # match-quality regression
```

The API tests are worth reading before you touch auth:

| Test | Pins down |
|---|---|
| `JwtServiceTest` | A challenge token is never accepted as a session token |
| `MfaFlowTest` | Two-step login, single-use recovery codes, enrolment endpoints reject anonymous callers |
| `TotpReplayTest` | A spent code stays dead across a time-step boundary (uses a fake clock) |

---

## 8. Common tasks

**Add a UI string.** Add a key to `DICT` in `i18n.ts` with `en` and `hi`, then
use `{{ i18n.t('your.key') }}`. A missing key renders as the raw key — that's
your signal you forgot one.

**Add a scheme.** Append to `ai/data/schemes.json`, run `python seed.py`, restart
the AI service (or `POST /admin/seed`).

**Add an API endpoint.** Controller method → decide auth in `SecurityConfig`.
Beware: `/api/auth/**` is `permitAll()`, so anything under it that needs a login
must be matched **before** that line.

**Change the LLM prompt.** `ai/main.py` → `_reason_prompt()`. Re-run
`python eval.py` afterwards to check you didn't regress match quality.

**Add a database column.** Add the field to the entity. Hibernate's
`ddl-auto: update` creates it on next boot. No migration file.

---

## 9. Traps that will bite you

| Trap | Why |
|---|---|
| `/api/auth/**` is public | A new `/api/auth/something` endpoint is anonymous unless matched earlier in `SecurityConfig` |
| Two constructors on a Spring bean | Spring refuses to guess. Annotate one with `@Autowired` |
| Beans of the same type | e.g. `CorsConfigurationSource` also matches Spring MVC's `MvcHandlerMappingIntrospector`. Use `@Qualifier` |
| In-memory H2 | Restarting the API deletes every user. Set `DATABASE_URL` to persist |
| Free-tier cold start | The first `/match` after ~15 min idle can take a minute or 503 |
| `zone.js` is absent | This app is zoneless. If the UI doesn't update, you mutated a plain field instead of a signal |
| Input font size | Anything under 16px makes iOS Safari zoom on focus |
| Rotating `YOJANAMITRA_MFA_ENC_KEY` | Every existing MFA enrolment becomes undecryptable |

---

## 10. Configuration reference

| Variable | Default | Service | Purpose |
|---|---|---|---|
| `DATABASE_URL` | — | api | Postgres connection string; auto-activates the postgres profile |
| `DB_ENGINE` | `h2` | api | Alternative profile switch when `DATABASE_URL` is unset |
| `YOJANAMITRA_JWT_SECRET` | dev secret | api | JWT signing key. Length picks HS256/384/512 |
| `YOJANAMITRA_MFA_ENC_KEY` | dev key | api | Encrypts TOTP secrets at rest |
| `YOJANAMITRA_AI_BASE_URL` | `http://localhost:8000` | api | Where the Python service lives |
| `YOJANAMITRA_CORS_ALLOWED_ORIGINS` | `http://localhost:4200` | api | Comma-separated allowed origins |
| `LLM_PROVIDER` | `ollama` | ai | `ollama` or `groq` |
| `EMBED_PROVIDER` | `ollama` | ai | `ollama`, `jina`, or `fastembed` |
| `GROQ_API_KEY` / `JINA_API_KEY` | — | ai | Required by the cloud providers |
| `OLLAMA_HOST` | `http://localhost:11434` | ai | Local model server |

---

## 11. Where to ask

- *"Why is it built this way?"* → [ARCHITECTURE.md](ARCHITECTURE.md)
- *"What can it do?"* → [FEATURES.md](FEATURES.md)
- *"How do I deploy it?"* → [DEPLOY.md](DEPLOY.md)
- *"Why does this line exist?"* → the code comments explain the *why*; the code
  itself shows the *what*.
