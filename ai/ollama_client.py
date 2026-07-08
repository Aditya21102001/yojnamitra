"""Thin client over the local Ollama REST API (embeddings + chat/generation).

Everything runs locally against http://localhost:11434 — no API keys, no cost.
Override models/host with env vars: OLLAMA_HOST, CHAT_MODEL, EMBED_MODEL.
"""
from __future__ import annotations

import json
import os
from typing import Any

import httpx

OLLAMA_HOST = os.getenv("OLLAMA_HOST", "http://localhost:11434").rstrip("/")
CHAT_MODEL = os.getenv("CHAT_MODEL", "llama3.2:1b")
EMBED_MODEL = os.getenv("EMBED_MODEL", "nomic-embed-text")

# The LLM can be slow on first token (cold model load), so use a generous timeout.
_TIMEOUT = httpx.Timeout(180.0, connect=10.0)

# Chat generation options tuned for modest hardware:
#  - num_ctx 2048: our prompts are short; a small KV cache saves memory.
#  - num_gpu 0: run the chat model on CPU. A small GPU (e.g. 2GB MX250) can't fit
#    the model + KV cache and Ollama OOMs; CPU inference is reliable here.
#    (Embeddings still use the GPU — they're tiny.) Override by editing this dict.
_OPTIONS = {"num_ctx": 2048, "num_gpu": 0}


class OllamaError(RuntimeError):
    """Raised when Ollama is unreachable or returns an error."""


def _post(path: str, payload: dict[str, Any]) -> dict[str, Any]:
    try:
        resp = httpx.post(f"{OLLAMA_HOST}{path}", json=payload, timeout=_TIMEOUT)
        resp.raise_for_status()
        return resp.json()
    except httpx.HTTPError as exc:  # connection refused, timeout, non-2xx, etc.
        raise OllamaError(
            f"Ollama request to {path} failed: {exc}. "
            f"Is Ollama running and are the models pulled? "
            f"Try: `ollama pull {CHAT_MODEL}` and `ollama pull {EMBED_MODEL}`."
        ) from exc


def embed(text: str) -> list[float]:
    """Return the embedding vector for a piece of text."""
    data = _post("/api/embeddings", {"model": EMBED_MODEL, "prompt": text})
    vec = data.get("embedding")
    if not vec:
        raise OllamaError(f"No embedding returned for model {EMBED_MODEL}: {data}")
    return vec


def generate(prompt: str, system: str | None = None) -> str:
    """Free-form text generation (used by the chat endpoint)."""
    messages = []
    if system:
        messages.append({"role": "system", "content": system})
    messages.append({"role": "user", "content": prompt})
    data = _post(
        "/api/chat",
        {"model": CHAT_MODEL, "messages": messages, "stream": False, "options": _OPTIONS},
    )
    return data.get("message", {}).get("content", "").strip()


def generate_json(prompt: str, system: str | None = None) -> Any:
    """Generation constrained to valid JSON (Ollama `format: json`)."""
    messages = []
    if system:
        messages.append({"role": "system", "content": system})
    messages.append({"role": "user", "content": prompt})
    data = _post(
        "/api/chat",
        {
            "model": CHAT_MODEL,
            "messages": messages,
            "stream": False,
            "format": "json",
            "options": _OPTIONS,
        },
    )
    content = data.get("message", {}).get("content", "").strip()
    try:
        return json.loads(content)
    except json.JSONDecodeError as exc:
        raise OllamaError(f"Model did not return valid JSON: {content[:300]}") from exc


def health() -> dict[str, Any]:
    """Report whether Ollama is reachable and which models are configured."""
    try:
        resp = httpx.get(f"{OLLAMA_HOST}/api/tags", timeout=httpx.Timeout(5.0))
        resp.raise_for_status()
        installed = [m.get("name", "") for m in resp.json().get("models", [])]
        return {
            "reachable": True,
            "host": OLLAMA_HOST,
            "chat_model": CHAT_MODEL,
            "embed_model": EMBED_MODEL,
            "chat_model_installed": any(CHAT_MODEL in n for n in installed),
            "embed_model_installed": any(EMBED_MODEL in n for n in installed),
        }
    except httpx.HTTPError:
        return {
            "reachable": False,
            "host": OLLAMA_HOST,
            "chat_model": CHAT_MODEL,
            "embed_model": EMBED_MODEL,
        }
