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
| Frontend     | Angular 21 + SCSS                                           |
| API / gateway| Spring Boot 3.3, Java 17, Spring Data JPA, H2 (→ Postgres)  |
| GenAI service| Python 3.13, FastAPI (in-memory cosine retrieval)          |
| LLM + embeds | Ollama — `llama3.2:1b` + `nomic-embed-text` (100% local)   |
| Auth         | Spring Security + JWT (HS256, jjwt), BCrypt passwords       |
| Data         | Curated Indian government schemes (`ai/data/schemes.json`)  |

> The chat model runs on **CPU** (`num_gpu:0` in `ai/ollama_client.py`) so it works
> on machines with a small or no GPU; embeddings use the GPU when available.

See [ARCHITECTURE.md](ARCHITECTURE.md) for the full design and data flow.

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

| Method | Endpoint        | Auth | Purpose                                                        |
| ------ | --------------- | ---- | ------------------------------------------------------------- |
| POST   | `/auth/register`| —    | Create an account → `{ token, username }`                     |
| POST   | `/auth/login`   | —    | Log in → `{ token, username }`                                |
| GET    | `/auth/me`      | —    | Current user (from the token, if present)                     |
| GET    | `/health`       | —    | API + GenAI + Ollama status                                   |
| GET    | `/schemes`      | —    | The full curated scheme list                                  |
| POST   | `/match`        | opt. | Profile → ranked, explained schemes (saved to your history if logged in) |
| POST   | `/chat`         | —    | Ask a question about one scheme                               |
| POST   | `/saved`        | 🔒   | Save a scheme                                                 |
| GET    | `/saved`        | 🔒   | Your saved schemes                                            |
| DELETE | `/saved/{id}`   | 🔒   | Remove a saved scheme                                         |
| GET    | `/history`      | 🔒   | Your recent matches                                           |

**Auth flow:** `register`/`login` return a 24-hour HS256 **JWT**. Send it as
`Authorization: Bearer <token>` on 🔒 calls. The Angular app stores the token in
`localStorage` and attaches it automatically via an HTTP interceptor; a route
guard protects `/dashboard`.

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
| `YOJANAMITRA_JWT_SECRET`  | dev secret         | API — JWT signing key (set in prod)  |
| `DB_ENGINE`               | `h2`               | API — set `postgres` to use Postgres |
| `DB_HOST` / `DB_PORT` / `DB_NAME` / `DB_USER` / `DB_PASSWORD` | localhost:5432/yojanamitra | API — Postgres connection |
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
- **Next** — live myscheme.gov.in ingestion at scale; a hosted-LLM deploy path
  (free tiers can't run Ollama — see DEPLOY.md).
