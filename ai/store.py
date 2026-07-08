"""Tiny in-memory vector store for schemes.

The dataset is small (a curated list), so we don't need a vector database. We
embed every scheme once via Ollama, persist the vectors to data/index.json, and
rank by cosine similarity in pure Python at query time. Same retrieval concept as
a vector DB, zero native dependencies (works anywhere Python runs).
"""
from __future__ import annotations

import json
import math
from pathlib import Path
from typing import Any

import ollama_client

_BASE = Path(__file__).parent
_INDEX_FILE = _BASE / "data" / "index.json"
_SCHEMES_FILE = _BASE / "data" / "schemes.json"

# Cached index: list of {"id", "vector", "scheme"}
_cache: list[dict[str, Any]] | None = None


def load_schemes() -> list[dict[str, Any]]:
    """Read the curated scheme dataset from disk."""
    with open(_SCHEMES_FILE, encoding="utf-8") as fh:
        return json.load(fh)


def _index_text(scheme: dict[str, Any]) -> str:
    """The text that gets embedded for a scheme (name + what it is + who it's for)."""
    return (
        f"{scheme['name']}. Category: {scheme['category']}. "
        f"{scheme['description']} Eligibility: {scheme['eligibility']} "
        f"Benefits: {scheme['benefits']} Tags: {', '.join(scheme.get('tags', []))}."
    )


def build_index() -> int:
    """(Re)build the vector index from schemes.json. Returns the number indexed."""
    global _cache
    schemes = load_schemes()
    entries = [
        {"id": s["id"], "vector": ollama_client.embed(_index_text(s)), "scheme": s}
        for s in schemes
    ]
    with open(_INDEX_FILE, "w", encoding="utf-8") as fh:
        json.dump(entries, fh)
    _cache = entries
    return len(entries)


def _load_cache() -> list[dict[str, Any]]:
    global _cache
    if _cache is None:
        if _INDEX_FILE.exists():
            with open(_INDEX_FILE, encoding="utf-8") as fh:
                _cache = json.load(fh)
        else:
            _cache = []
    return _cache


def is_seeded() -> bool:
    return len(_load_cache()) > 0


def ensure_seeded() -> None:
    """Build the index on first use if it hasn't been built yet."""
    if not is_seeded():
        build_index()


def _cosine(a: list[float], b: list[float]) -> float:
    dot = sum(x * y for x, y in zip(a, b))
    na = math.sqrt(sum(x * x for x in a))
    nb = math.sqrt(sum(y * y for y in b))
    return dot / (na * nb) if na and nb else 0.0


def search(query: str, top_k: int) -> list[dict[str, Any]]:
    """Return the top_k schemes for a query, each with a `score` in [0, 1]."""
    ensure_seeded()
    qv = ollama_client.embed(query)
    scored: list[tuple[float, dict[str, Any]]] = []
    for entry in _load_cache():
        sim = _cosine(qv, entry["vector"])
        scheme = dict(entry["scheme"])
        scheme["score"] = round(max(0.0, sim), 3)
        scored.append((sim, scheme))
    scored.sort(key=lambda pair: pair[0], reverse=True)
    return [scheme for _, scheme in scored[:top_k]]


def get_scheme(scheme_id: str) -> dict[str, Any] | None:
    for s in load_schemes():
        if s["id"] == scheme_id:
            return s
    return None
