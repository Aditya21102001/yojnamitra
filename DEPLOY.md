# Deploying YojanaMitra (free: Groq + Jina + Render + Neon)

The app runs a local LLM in development; for a free public demo it swaps to
**hosted, free-tier providers** via a small provider layer (`ai/providers.py`) —
no code changes, just env vars:

| Concern    | Local (dev)                | Cloud (deploy)              |
| ---------- | -------------------------- | --------------------------- |
| LLM        | Ollama `llama3.2:1b`       | **Groq** `llama-3.1-8b-instant` (sub-second, free tier) |
| Embeddings | Ollama `nomic-embed-text`  | **Jina** `jina-embeddings-v2-base-en` (free tier)       |
| Database   | H2 in-memory               | **Neon / Render Postgres** (via `DATABASE_URL`)          |
| Hosting    | localhost                  | **Render** (web static + 2 Docker web services + DB)     |

Everything stays $0. Provider selection is `LLM_PROVIDER` / `EMBED_PROVIDER`.

## 0. Free API keys (2 min)
- **Groq**: console.groq.com → API Keys → create → `GROQ_API_KEY`
- **Jina**: jina.ai → get the free embeddings API key → `JINA_API_KEY`
- **Neon**: your existing project's connection string (or let Render create a Postgres)

## 1. Try the cloud config locally first (optional but recommended)
```bash
cd ai
LLM_PROVIDER=groq EMBED_PROVIDER=jina \
GROQ_API_KEY=... JINA_API_KEY=... \
  .venv/Scripts/python seed.py            # builds the index via Jina
LLM_PROVIDER=groq EMBED_PROVIDER=jina GROQ_API_KEY=... JINA_API_KEY=... \
  .venv/Scripts/python -m uvicorn main:app --port 8000
# GET /health should show llm_provider=groq, embed_provider=jina
```

## 2. Deploy on Render (Blueprint)
1. Push to GitHub (done).
2. Render → **New → Blueprint** → pick this repo. `render.yaml` provisions:
   `yojanamitra-ai` (Docker), `yojanamitra-api` (Docker), `yojanamitra-web`
   (static), and a free `yojanamitra-db` (Postgres).
3. Fill the secrets Render prompts for:
   - on **yojanamitra-ai**: `GROQ_API_KEY`, `JINA_API_KEY`
   - on **yojanamitra-api**: `YOJANAMITRA_AI_BASE_URL` = the ai service URL,
     `YOJANAMITRA_CORS_ALLOWED_ORIGINS` = the web URL,
     `YOJANAMITRA_WEB_BASE_URL` = the web URL (used to build reset links)
     (`DATABASE_URL`, the JWT secret and the MFA encryption key are wired automatically.)
4. Point the web app at the API: set `apiBase` in
   `web/src/environments/environment.prod.ts` to `https://<api>.onrender.com/api`,
   commit, and Render rebuilds the static site.

### 2a. Confirm you are actually on Postgres

The single most common misconfiguration. If `DATABASE_URL` is unset the API
**silently falls back to in-memory H2** and every user, password, MFA enrolment
and reset token is wiped on each restart or idle spin-down. On boot the api log
must contain:

```
[YojanaMitra] DATABASE_URL detected -> jdbc:postgresql://…
```

No line, no database. Note that **Render's free Postgres is deleted after 30
days** — for something durable, delete the `databases:` block from `render.yaml`,
switch `DATABASE_URL` to `sync: false`, and paste a Neon connection string
(free tier, permanent) into the dashboard.

### 2b. Email for password reset

`yojanamitra.mail.provider` defaults to **`log`**: nothing is emailed and the
reset link is printed into the api service's logs. That is fine for a demo, but
it means password reset does not work for real users, and anyone who can read
the logs can seize an account.

For real delivery, set on **yojanamitra-api**:

| Variable | Value |
| -------- | ----- |
| `YOJANAMITRA_MAIL_PROVIDER` | `brevo` |
| `BREVO_API_KEY` | from brevo.com → SMTP & API → API keys (free: 300 mails/day) |
| `YOJANAMITRA_MAIL_FROM` | a sender address you have **verified** in Brevo |

Brevo rather than Resend because its free tier delivers to any recipient once a
single sender is verified, whereas Resend's free tier requires a domain you own.
The HTTP API is used rather than SMTP because Render blocks outbound SMTP ports.

> `BrevoEmailSender` fails fast at startup if `YOJANAMITRA_MAIL_PROVIDER=brevo`
> but `BREVO_API_KEY` is empty — so set both, or neither.

> Render service URLs are predictable — `https://<name>.onrender.com` — so if the
> names aren't taken you can fill all three URLs up front:
> api `https://yojanamitra-api.onrender.com`, web `https://yojanamitra-web.onrender.com`,
> ai `https://yojanamitra-ai.onrender.com`.

## Notes / caveats
- **Render free** services **spin down when idle** → first request after a nap is
  a ~30–60s cold start. Fine for a portfolio demo.
- **Groq/Jina free tiers** have rate limits — plenty for a demo, not production load.
- Prefer **Neon** for the DB? Skip the `databases:` block and set
  `DATABASE_URL` on the api service to your Neon connection string instead.

## Alternatives
- **No embed API** (self-contained): set `EMBED_PROVIDER=fastembed` and install
  `requirements-deploy.txt` in `ai/Dockerfile` (needs ~400MB RAM — use a paid/
  larger instance or an Oracle Always-Free VM).
- **Keep the local LLM in the cloud**: run the whole stack (Ollama + services) on
  an **Oracle Cloud Always-Free ARM VM** (24GB RAM) via `docker compose`.

## Containerize locally (Docker)
```bash
docker build -t ym-ai ./ai && docker build -t ym-api ./api && docker build -t ym-web ./web
docker run -p 8000:8000 -e GROQ_API_KEY=... -e JINA_API_KEY=... ym-ai
docker run -p 8080:8080 -e DATABASE_URL="postgresql://…neon.tech/neondb?sslmode=require" \
  -e YOJANAMITRA_AI_BASE_URL=http://host.docker.internal:8000 \
  -e YOJANAMITRA_CORS_ALLOWED_ORIGINS=http://localhost:4200 -e YOJANAMITRA_JWT_SECRET=dev ym-api
docker run -p 4200:80 ym-web
```
