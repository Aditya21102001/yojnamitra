"""YojanaMitra GenAI service.

Pipeline for a match:
  profile -> natural-language query -> embed -> vector search (Chroma)
          -> LLM eligibility reasoning per candidate -> ranked, explained results.

The LLM only *reasons about eligibility and phrases it simply* — it never invents
schemes. Candidates always come from the curated dataset via retrieval.
"""
from __future__ import annotations

import json
from typing import Any

from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware

import ollama_client
import store
from models import (
    ChatRequest,
    ChatResponse,
    MatchedScheme,
    MatchRequest,
    MatchResponse,
)

app = FastAPI(title="YojanaMitra GenAI Service", version="0.1.0")

# The Spring Boot API is the normal caller, but allow the Angular dev server too
# so the service can be exercised directly during development.
app.add_middleware(
    CORSMiddleware,
    allow_origins=["http://localhost:8080", "http://localhost:4200"],
    allow_methods=["*"],
    allow_headers=["*"],
)

_VERDICTS = {"eligible", "maybe", "not_eligible"}

_SYSTEM = (
    "You are YojanaMitra, an assistant that assesses a citizen's eligibility for "
    "Indian government welfare schemes. You are given a citizen profile and a list "
    "of candidate schemes. For each scheme, decide whether the citizen is "
    "'eligible', 'maybe' eligible (missing information or borderline), or "
    "'not_eligible'. Base your decision ONLY on the eligibility text provided; do "
    "not invent rules. Explain in one or two simple sentences a non-expert can "
    "understand. Never recommend a scheme the person clearly cannot get."
)


def _reason_prompt(query: str, candidates: list[dict[str, Any]], lang: str = "en") -> str:
    lines = [
        "CITIZEN PROFILE:",
        query,
        "",
        "CANDIDATE SCHEMES:",
    ]
    for s in candidates:
        lines.append(
            f"- id: {s['id']}\n"
            f"  name: {s['name']}\n"
            f"  eligibility: {s['eligibility']}\n"
            f"  benefits: {s['benefits']}"
        )
    lines += [
        "",
        "Return ONLY JSON of this exact shape:",
        '{"results": [{"id": "<scheme id>", "verdict": "eligible|maybe|not_eligible", '
        '"reason": "<one or two simple sentences>", '
        '"how_to_apply": "<one short sentence>"}]}',
        "Include one entry for every candidate id above.",
    ]
    if lang == "hi":
        lines.append(
            "IMPORTANT: Keep 'verdict' in English (eligible/maybe/not_eligible), but write "
            "the 'reason' and 'how_to_apply' values in simple Hindi (Devanagari script)."
        )
    return "\n".join(lines)


def _rank_key(scheme: MatchedScheme) -> tuple[int, float]:
    order = {"eligible": 0, "maybe": 1, "not_eligible": 2}
    return (order.get(scheme.verdict, 3), -scheme.score)


@app.get("/health")
def health() -> dict[str, Any]:
    return {
        "status": "ok",
        "seeded": store.is_seeded(),
        "ollama": ollama_client.health(),
    }


@app.post("/admin/seed")
def seed() -> dict[str, Any]:
    n = store.build_index()
    return {"indexed": n}


@app.get("/schemes")
def list_schemes() -> list[dict[str, Any]]:
    return store.load_schemes()


@app.post("/match", response_model=MatchResponse)
def match(req: MatchRequest) -> MatchResponse:
    query = req.profile.to_query()
    try:
        candidates = store.search(query, req.top_k)
    except ollama_client.OllamaError as exc:
        raise HTTPException(status_code=503, detail=str(exc)) from exc

    if not candidates:
        return MatchResponse(query=query, count=0, schemes=[])

    # Ask the LLM to reason about eligibility for all candidates in one call.
    try:
        raw = ollama_client.generate_json(_reason_prompt(query, candidates, req.lang), system=_SYSTEM)
        verdicts = {r["id"]: r for r in raw.get("results", []) if "id" in r}
    except ollama_client.OllamaError:
        verdicts = {}  # degrade gracefully to retrieval-only results

    hi = req.lang == "hi"
    fallback_reason = (
        "आपकी जानकारी के आधार पर यह योजना आपसे मेल खाती है।" if hi
        else "Matched to your situation based on the scheme details."
    )

    matched: list[MatchedScheme] = []
    for s in candidates:
        v = verdicts.get(s["id"], {})
        verdict = v.get("verdict", "maybe")
        if verdict not in _VERDICTS:
            verdict = "maybe"
        fallback_apply = (
            f"{s['apply_url']} पर ऑनलाइन आवेदन करें।" if hi
            else f"Apply online at {s['apply_url']}."
        )
        matched.append(
            MatchedScheme(
                id=s["id"],
                name=s["name"],
                ministry=s["ministry"],
                category=s["category"],
                description=s["description"],
                benefits=s["benefits"],
                apply_url=s["apply_url"],
                verdict=verdict,
                reason=v.get("reason") or fallback_reason,
                how_to_apply=v.get("how_to_apply") or fallback_apply,
                score=s["score"],
            )
        )

    matched.sort(key=_rank_key)
    return MatchResponse(query=query, count=len(matched), schemes=matched)


@app.post("/chat", response_model=ChatResponse)
def chat(req: ChatRequest) -> ChatResponse:
    scheme = store.get_scheme(req.scheme_id)
    if scheme is None:
        raise HTTPException(status_code=404, detail=f"Unknown scheme: {req.scheme_id}")

    context = (
        f"Scheme: {scheme['name']}\n"
        f"Ministry: {scheme['ministry']}\n"
        f"Description: {scheme['description']}\n"
        f"Eligibility: {scheme['eligibility']}\n"
        f"Benefits: {scheme['benefits']}\n"
        f"Where to apply: {scheme['apply_url']}"
    )
    system = (
        "You answer citizen questions about a single Indian government scheme using "
        "ONLY the provided details. If the answer isn't in the details, say so and "
        "point them to the official website. Keep it short and simple."
    )
    if req.lang == "hi":
        system += " Answer in simple Hindi (Devanagari script)."
    prompt = f"{context}\n\nQuestion: {req.question}\n\nAnswer:"
    try:
        answer = ollama_client.generate(prompt, system=system)
    except ollama_client.OllamaError as exc:
        raise HTTPException(status_code=503, detail=str(exc)) from exc
    return ChatResponse(scheme_id=req.scheme_id, answer=answer)
