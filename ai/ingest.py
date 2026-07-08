"""YojanaMitra ingestion pipeline (Phase 3).

Pull schemes from a source, normalize them to our canonical schema, merge into
data/schemes.json (dedupe/update by id), and rebuild the vector index.

    python ingest.py --source extra       # add the bundled 'extra' batch (offline)
    python ingest.py --source curated     # just re-index the current dataset
    python ingest.py --source data_gov    # live: needs DATA_GOV_API_KEY + DATA_GOV_RESOURCE_ID
    python ingest.py --source myscheme     # live/best-effort: myscheme.gov.in public API

The offline sources always work. The live sources normalize third-party fields
into our schema and are gated behind env vars — government endpoints change and
may rate-limit, so treat them as best-effort.
"""
from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
from typing import Any, Callable

import httpx

import store

_BASE = Path(__file__).parent
_DATASET = _BASE / "data" / "schemes.json"
_EXTRA = _BASE / "data" / "schemes_extra.json"


# --------------------------------------------------------------------------- #
# Normalisation
# --------------------------------------------------------------------------- #
def normalize(raw: dict[str, Any]) -> dict[str, Any]:
    """Coerce a raw scheme dict into the canonical schema with safe defaults."""
    s = dict(raw)
    if not s.get("id") or not s.get("name"):
        raise ValueError(f"scheme missing id/name: {raw!r}")
    for key in ("ministry", "category", "description", "eligibility", "benefits", "apply_url"):
        s.setdefault(key, "")
    s.setdefault("category", "General")
    s.setdefault("gender", "any")
    s.setdefault("states", ["ALL"])
    s.setdefault("target_groups", [])
    for key in ("min_age", "max_age", "income_limit"):
        s.setdefault(key, None)
    tags = s.get("tags", [])
    if isinstance(tags, str):
        tags = [t.strip() for t in tags.split(",") if t.strip()]
    s["tags"] = tags or []
    return s


# --------------------------------------------------------------------------- #
# Sources — each returns a list of raw scheme dicts
# --------------------------------------------------------------------------- #
def source_curated() -> list[dict[str, Any]]:
    return json.loads(_DATASET.read_text(encoding="utf-8"))


def source_extra() -> list[dict[str, Any]]:
    return json.loads(_EXTRA.read_text(encoding="utf-8"))


def source_data_gov(limit: int) -> list[dict[str, Any]]:
    key = os.getenv("DATA_GOV_API_KEY")
    resource = os.getenv("DATA_GOV_RESOURCE_ID")
    if not key or not resource:
        raise SystemExit(
            "The data_gov source needs DATA_GOV_API_KEY and DATA_GOV_RESOURCE_ID "
            "(get a free key at https://data.gov.in and pick a scheme dataset resource id)."
        )
    resp = httpx.get(
        f"https://api.data.gov.in/resource/{resource}",
        params={"api-key": key, "format": "json", "limit": limit},
        timeout=30,
    )
    resp.raise_for_status()
    records = resp.json().get("records", [])
    out: list[dict[str, Any]] = []
    for i, rec in enumerate(records):
        out.append({
            "id": rec.get("id") or rec.get("scheme_code") or f"datagov-{i}",
            "name": rec.get("scheme_name") or rec.get("name") or "Unknown scheme",
            "ministry": rec.get("ministry") or rec.get("department") or "",
            "category": rec.get("sector") or rec.get("category") or "General",
            "description": rec.get("description") or rec.get("details") or "",
            "eligibility": rec.get("eligibility") or "",
            "benefits": rec.get("benefits") or "",
            "apply_url": rec.get("url") or "https://www.myscheme.gov.in",
            "tags": [],
        })
    return out


def source_myscheme(limit: int) -> list[dict[str, Any]]:
    if os.getenv("MYSCHEME_ENABLE") != "1":
        raise SystemExit(
            "The myscheme source is best-effort (the site's public API can change or "
            "rate-limit). Set MYSCHEME_ENABLE=1 (and optionally MYSCHEME_API_KEY) to try it."
        )
    resp = httpx.get(
        "https://api.myscheme.gov.in/search/v4/schemes",
        params={"lang": "en", "q": "[]", "keyword": "", "sort": "", "from": "0", "size": str(limit)},
        headers={"x-api-key": os.getenv("MYSCHEME_API_KEY", "")},
        timeout=30,
    )
    resp.raise_for_status()
    items = resp.json().get("data", {}).get("hits", {}).get("items", [])
    out: list[dict[str, Any]] = []
    for it in items:
        f = it.get("fields", it)
        ministry = f.get("nodalMinistryName")
        if isinstance(ministry, dict):
            ministry = ministry.get("label", "")
        category = f.get("schemeCategory")
        if isinstance(category, list):
            category = ", ".join(category)
        out.append({
            "id": f.get("slug") or f.get("schemeShortTitle") or f.get("id"),
            "name": f.get("schemeName") or f.get("schemeShortTitle") or "Unknown scheme",
            "ministry": ministry or "",
            "category": category or "General",
            "description": f.get("briefDescription") or "",
            "eligibility": f.get("eligibilityDescription_md") or "",
            "benefits": f.get("benefits_md") or "",
            "apply_url": f"https://www.myscheme.gov.in/schemes/{f.get('slug', '')}",
            "tags": f.get("tags", []) or [],
        })
    return out


SOURCES: dict[str, Callable[..., list[dict[str, Any]]]] = {
    "curated": source_curated,
    "extra": source_extra,
    "data_gov": source_data_gov,
    "myscheme": source_myscheme,
}
_LIVE = {"data_gov", "myscheme"}


# --------------------------------------------------------------------------- #
# Merge + run
# --------------------------------------------------------------------------- #
def merge_into_dataset(new_schemes: list[dict[str, Any]]) -> tuple[int, int, int]:
    existing = {s["id"]: s for s in json.loads(_DATASET.read_text(encoding="utf-8"))}
    added = updated = 0
    for raw in new_schemes:
        s = normalize(raw)
        if s["id"] in existing:
            updated += 1
        else:
            added += 1
        existing[s["id"]] = s
    merged = list(existing.values())
    _DATASET.write_text(json.dumps(merged, indent=2, ensure_ascii=False), encoding="utf-8")
    return added, updated, len(merged)


def main() -> None:
    parser = argparse.ArgumentParser(description="Ingest schemes and rebuild the vector index.")
    parser.add_argument("--source", choices=list(SOURCES), default="extra")
    parser.add_argument("--limit", type=int, default=50)
    parser.add_argument("--no-index", action="store_true", help="skip rebuilding the vector index")
    args = parser.parse_args()

    print(f"Fetching from source: {args.source}")
    fetch = SOURCES[args.source]
    raw = fetch(args.limit) if args.source in _LIVE else fetch()
    print(f"  got {len(raw)} scheme(s)")

    added, updated, total = merge_into_dataset(raw)
    print(f"Merged into dataset: +{added} new, {updated} updated, {total} total.")

    if not args.no_index:
        print("Rebuilding vector index (embedding via Ollama)...")
        n = store.build_index()
        print(f"  index rebuilt with {n} schemes.")
    print("Done. If the API is running, POST /admin/seed to refresh its in-memory index.")


if __name__ == "__main__":
    main()
