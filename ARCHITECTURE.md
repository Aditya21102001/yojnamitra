# YojanaMitra — Architecture

## 1. What it does

A citizen describes their situation (age, occupation, income, category, state,
free text). The system returns the government welfare schemes they are most
likely eligible for, each with a plain-language **reason** and a **how-to-apply**
line — and lets them ask follow-up questions about any scheme.

The AI **never invents schemes**. Candidate schemes always come from a curated,
auditable dataset via semantic retrieval; the LLM only *reasons about
eligibility* and *phrases things simply*. This keeps the system trustworthy — the
right property for a public-benefits tool.

## 2. Three services (why the split)

```
 Angular (web/, :4200)          Spring Boot (api/, :8080)          Python FastAPI (ai/, :8000)
 ┌───────────────────┐          ┌───────────────────────┐         ┌──────────────────────────┐
 │ profile form      │  POST    │ /api/match            │  POST   │ /match                   │
 │ results + badges  │ ───────► │ - validate            │ ──────► │ - profile -> query text  │
 │ inline chat       │  JSON    │ - camelCase→snake     │  JSON   │ - embed (Ollama)         │
 │                   │ ◄─────── │ - forward to AI       │ ◄────── │ - vector search (cosine) │
 └───────────────────┘          │ - save MatchHistory   │         │ - LLM eligibility reason │
                                │   (JPA → H2)          │         └───────────┬──────────────┘
                                └───────────────────────┘                     │
                                                                    ┌─────────▼─────────┐
                                                                    │ Ollama  :11434    │
                                                                    │ llama3.2:1b       │
                                                                    │ nomic-embed-text  │
                                                                    └───────────────────┘
```

| Service       | Responsibility                                                             | Why it's its own tier |
| ------------- | -------------------------------------------------------------------------- | --------------------- |
| **web/**      | UI, form, presentation                                                      | Standard SPA boundary |
| **api/**      | System of record, validation, auth (later), orchestration, request history | Enterprise Java/JPA — the "backend of record" a company owns |
| **ai/**       | Embeddings, retrieval, LLM reasoning                                        | GenAI concerns isolated in Python where the ecosystem lives; swappable without touching business logic |

Angular talks **only** to Spring Boot. Spring Boot is the only caller of the
Python service. That single seam means auth, rate-limiting, caching, and history
all live in one place, and the AI service stays a stateless, replaceable engine.

## 3. The match pipeline (RAG + reasoning)

1. **Profile → query string.** `Profile.to_query()` flattens the form into one
   natural-language sentence.
2. **Embed.** The query is embedded with `nomic-embed-text` via Ollama.
3. **Retrieve.** The in-memory store ranks all schemes by cosine similarity and
   returns the top-K. Each scheme was indexed from its name + description +
   eligibility + benefits + tags, with a similarity score in `[0,1]`.
4. **Reason.** All candidates + the profile go to `llama3.2:1b` in a single
   `format: json` call. The model returns, per scheme, a verdict
   (`eligible` / `maybe` / `not_eligible`), a one-line reason, and a how-to-apply
   line — grounded strictly in the provided eligibility text.
5. **Rank & return.** Results are ordered eligible → maybe → not_eligible, then by
   retrieval score.

If the LLM call fails, the service **degrades gracefully** to retrieval-only
results (verdict defaults to `maybe`) instead of erroring out.

## 4. Data

`ai/data/schemes.json` — a curated set of real central-government schemes
(PM-KISAN, PMAY-G, Ayushman Bharat, Mudra, Sukanya Samriddhi, …). Each record has
`eligibility`, `benefits`, `apply_url`, `tags`, and coarse targeting fields. This
is the single source of truth; re-running `seed.py` rebuilds the vector index.

## 5. Tech choices (all free / open-source)

| Concern       | Choice                | Note                                             |
| ------------- | --------------------- | ------------------------------------------------ |
| LLM           | Ollama + llama3.2:1b  | Local, private, no API key. 1b fits a 2GB GPU; bump to 3b/8b via `CHAT_MODEL` with more memory. |
| Embeddings    | nomic-embed-text      | Local via Ollama                                 |
| Vector store  | In-memory cosine (Python) | Tiny dataset; vectors persisted to index.json, no native deps |
| API DB        | H2 in-memory          | Zero-config for MVP; swap to Postgres in Phase 2 |
| Contracts     | JSON over REST        | Loose coupling; Python is replaceable            |

## 6. Design decisions worth calling out

- **Retrieval-grounded, not generative discovery** — the LLM can't hallucinate a
  scheme that doesn't exist.
- **`format: json` for the reasoning call** — reliable parsing, no brittle regex.
- **Graceful degradation** — Ollama down ⇒ retrieval-only results; AI service down
  ⇒ Spring returns a clean 503 with a helpful message.
- **camelCase ↔ snake_case translation at the gateway** — the Java and Python
  idioms each stay natural; the Spring service owns the mapping.

## 7. Roadmap

- **Phase 2** — JWT auth (Spring Security), saved-matches dashboard, richer chat
  memory, Hindi/regional-language support (the models are multilingual).
- **Phase 3** — ingestion pipeline that pulls live scheme data from
  myscheme.gov.in / data.gov.in, an eval harness for match quality, and a free
  deploy (Render/Railway + a hosted Ollama or a small quantized model).
