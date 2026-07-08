# YojanaMitra GenAI service — cloud build (Groq LLM + Jina embeddings)
FROM python:3.13-slim

WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY . .

# Cloud defaults: Groq for reasoning, Jina for embeddings — both lightweight,
# free-tier, and need only httpx (fits a 512MB instance). Provide GROQ_API_KEY
# and JINA_API_KEY at deploy time.
#
# Alternative (self-contained, no embed API, needs ~400MB RAM): set
#   EMBED_PROVIDER=fastembed
# and install requirements-deploy.txt instead of requirements.txt above.
ENV LLM_PROVIDER=groq \
    EMBED_PROVIDER=jina \
    GROQ_MODEL=llama-3.1-8b-instant \
    JINA_MODEL=jina-embeddings-v2-base-en

EXPOSE 8000
# Try to build the vector index on boot, but NEVER let a seed failure crash the
# container — uvicorn always starts, and store.ensure_seeded() builds the index
# lazily on the first match if boot-seeding was skipped.
CMD ["sh", "-c", "python seed.py || echo 'seed skipped on boot; will build index lazily on first match'; uvicorn main:app --host 0.0.0.0 --port 8000"]
