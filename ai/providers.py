"""Pluggable LLM + embedding providers.

The same code runs locally on Ollama or in the cloud on Groq (LLM) + fastembed
(embeddings), selected by env vars — nothing else in the app changes.

    LLM_PROVIDER   = ollama (default) | groq
    EMBED_PROVIDER = ollama (default) | jina | fastembed
    GROQ_API_KEY, GROQ_MODEL       (default llama-3.1-8b-instant)
    JINA_API_KEY, JINA_MODEL       (default jina-embeddings-v2-base-en) — hosted, lightweight
    FASTEMBED_MODEL                (default BAAI/bge-small-en-v1.5) — local CPU, ~400MB RAM
    (plus the existing OLLAMA_HOST / CHAT_MODEL / EMBED_MODEL for the ollama path)

Callers use providers.embed / generate / generate_json / health and catch
providers.ProviderError, regardless of which backend is active.
"""
from __future__ import annotations

import json
import os
from typing import Any

import httpx

import ollama_client

LLM_PROVIDER = os.getenv("LLM_PROVIDER", "ollama").lower()
EMBED_PROVIDER = os.getenv("EMBED_PROVIDER", "ollama").lower()

GROQ_API_KEY = os.getenv("GROQ_API_KEY", "")
GROQ_MODEL = os.getenv("GROQ_MODEL", "llama-3.1-8b-instant")
GROQ_URL = "https://api.groq.com/openai/v1/chat/completions"

JINA_API_KEY = os.getenv("JINA_API_KEY", "")
JINA_MODEL = os.getenv("JINA_MODEL", "jina-embeddings-v2-base-en")
JINA_URL = "https://api.jina.ai/v1/embeddings"

FASTEMBED_MODEL = os.getenv("FASTEMBED_MODEL", "BAAI/bge-small-en-v1.5")

_TIMEOUT = httpx.Timeout(60.0, connect=10.0)


class ProviderError(RuntimeError):
    """Uniform error type across all providers."""


# --------------------------------------------------------------------------- #
# Embeddings
# --------------------------------------------------------------------------- #
_fastembed_model = None


def _fastembed_embed(text: str) -> list[float]:
    global _fastembed_model
    if _fastembed_model is None:
        try:
            from fastembed import TextEmbedding
        except ImportError as exc:  # pragma: no cover
            raise ProviderError(
                "EMBED_PROVIDER=fastembed but fastembed isn't installed "
                "(pip install -r requirements-deploy.txt)."
            ) from exc
        _fastembed_model = TextEmbedding(FASTEMBED_MODEL)
    vector = next(iter(_fastembed_model.embed([text])))
    return vector.tolist()


def _jina_embed(text: str) -> list[float]:
    if not JINA_API_KEY:
        raise ProviderError("EMBED_PROVIDER=jina but JINA_API_KEY is not set (free key at jina.ai).")
    try:
        resp = httpx.post(
            JINA_URL,
            json={"model": JINA_MODEL, "input": [text]},
            headers={"Authorization": f"Bearer {JINA_API_KEY}"},
            timeout=_TIMEOUT,
        )
        resp.raise_for_status()
        return resp.json()["data"][0]["embedding"]
    except httpx.HTTPError as exc:
        raise ProviderError(f"Jina embeddings request failed: {exc}") from exc


def embed(text: str) -> list[float]:
    try:
        if EMBED_PROVIDER == "jina":
            return _jina_embed(text)
        if EMBED_PROVIDER == "fastembed":
            return _fastembed_embed(text)
        return ollama_client.embed(text)
    except ollama_client.OllamaError as exc:
        raise ProviderError(str(exc)) from exc


# --------------------------------------------------------------------------- #
# LLM chat / generation
# --------------------------------------------------------------------------- #
def _groq_chat(messages: list[dict[str, str]], json_mode: bool = False) -> str:
    if not GROQ_API_KEY:
        raise ProviderError("LLM_PROVIDER=groq but GROQ_API_KEY is not set.")
    payload: dict[str, Any] = {"model": GROQ_MODEL, "messages": messages, "temperature": 0.2}
    if json_mode:
        payload["response_format"] = {"type": "json_object"}
    try:
        resp = httpx.post(
            GROQ_URL,
            json=payload,
            headers={"Authorization": f"Bearer {GROQ_API_KEY}"},
            timeout=_TIMEOUT,
        )
        resp.raise_for_status()
        return resp.json()["choices"][0]["message"]["content"].strip()
    except httpx.HTTPError as exc:
        detail = ""
        if isinstance(exc, httpx.HTTPStatusError):
            detail = f" ({exc.response.status_code}: {exc.response.text[:200]})"
        raise ProviderError(f"Groq request failed: {exc}{detail}") from exc


def _messages(prompt: str, system: str | None) -> list[dict[str, str]]:
    msgs = []
    if system:
        msgs.append({"role": "system", "content": system})
    msgs.append({"role": "user", "content": prompt})
    return msgs


def generate(prompt: str, system: str | None = None) -> str:
    try:
        if LLM_PROVIDER == "groq":
            return _groq_chat(_messages(prompt, system))
        return ollama_client.generate(prompt, system)
    except ollama_client.OllamaError as exc:
        raise ProviderError(str(exc)) from exc


def generate_json(prompt: str, system: str | None = None) -> Any:
    try:
        if LLM_PROVIDER == "groq":
            content = _groq_chat(_messages(prompt, system), json_mode=True)
            try:
                return json.loads(content)
            except json.JSONDecodeError as exc:
                raise ProviderError(f"Groq did not return valid JSON: {content[:200]}") from exc
        return ollama_client.generate_json(prompt, system)
    except ollama_client.OllamaError as exc:
        raise ProviderError(str(exc)) from exc


# --------------------------------------------------------------------------- #
# Health
# --------------------------------------------------------------------------- #
def health() -> dict[str, Any]:
    info: dict[str, Any] = {
        "llm_provider": LLM_PROVIDER,
        "embed_provider": EMBED_PROVIDER,
    }
    if LLM_PROVIDER == "groq":
        info["groq_model"] = GROQ_MODEL
        info["groq_key_set"] = bool(GROQ_API_KEY)
    if EMBED_PROVIDER == "jina":
        info["jina_model"] = JINA_MODEL
        info["jina_key_set"] = bool(JINA_API_KEY)
    if EMBED_PROVIDER == "fastembed":
        info["fastembed_model"] = FASTEMBED_MODEL
    if LLM_PROVIDER == "ollama" or EMBED_PROVIDER == "ollama":
        info["ollama"] = ollama_client.health()
    return info
