# YojanaMitra — GenAI service (`ai/`)

FastAPI service that does the actual matching: retrieval over the scheme dataset
+ LLM eligibility reasoning, all local via Ollama (in-memory cosine retrieval).

## Setup
```bash
python -m venv .venv
.venv\Scripts\activate            # Windows
pip install -r requirements.txt
python seed.py                    # build the vector index (Ollama must be running)
uvicorn main:app --reload --port 8000
```

## Endpoints
| Method | Path          | Purpose                                             |
| ------ | ------------- | --------------------------------------------------- |
| GET    | `/health`     | Service + Ollama status, whether the index is built |
| POST   | `/match`      | `{profile, top_k}` → ranked, explained schemes      |
| POST   | `/chat`       | `{scheme_id, question}` → grounded answer            |
| GET    | `/schemes`    | The full curated scheme list                         |
| POST   | `/admin/seed` | Rebuild the vector index                             |

## Config (env vars)
| Var           | Default                  |
| ------------- | ------------------------ |
| `OLLAMA_HOST` | `http://localhost:11434` |
| `CHAT_MODEL`  | `llama3.2:1b`            |
| `EMBED_MODEL` | `nomic-embed-text`       |

## Quick test
```bash
curl -X POST http://localhost:8000/match -H "Content-Type: application/json" -d "{\"profile\":{\"age\":26,\"gender\":\"female\",\"state\":\"Bihar\",\"occupation\":\"small farmer\",\"annual_income\":90000,\"category\":\"SC\"}}"
```
