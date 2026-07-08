"""Retrieval-quality eval for YojanaMitra matching.

For each labeled profile, checks whether an expected scheme id appears in the
top-k retrieved schemes, and reports hit@1, hit@3, and MRR. It uses the same
embedding retrieval as /match (no LLM), so it is fast and deterministic — a
guardrail against dataset/embedding regressions.

    python eval.py
"""
from __future__ import annotations

import store
from models import Profile

# Each case: a profile and the scheme id(s) that SHOULD surface (any counts as a hit).
CASES: list[dict] = [
    {"desc": "landholding farmer", "profile": {"occupation": "small farmer", "description": "I own farmland"}, "expect": ["pm-kisan", "kisan-credit-card"]},
    {"desc": "girl child savings", "profile": {"gender": "female", "age": 5, "description": "save money for my daughter's education"}, "expect": ["sukanya-samriddhi"]},
    {"desc": "street vendor loan", "profile": {"occupation": "street vendor", "description": "need working capital loan"}, "expect": ["pm-svanidhi"]},
    {"desc": "poor family health cover", "profile": {"annual_income": 50000, "description": "poor family, need hospital insurance"}, "expect": ["ayushman-bharat-pmjay"]},
    {"desc": "SC student scholarship", "profile": {"category": "SC", "occupation": "student", "age": 17, "description": "need help with college fees"}, "expect": ["national-scholarship-postmatric"]},
    {"desc": "woman entrepreneur", "profile": {"gender": "female", "description": "start a new manufacturing business, need a large loan"}, "expect": ["stand-up-india", "pm-mudra"]},
    {"desc": "BPL woman LPG", "profile": {"gender": "female", "description": "poor household, need a free cooking gas connection"}, "expect": ["pm-ujjwala"]},
    {"desc": "elderly pension", "profile": {"age": 68, "description": "old, no income, need a monthly pension"}, "expect": ["nsap-ignoaps", "atal-pension-yojana"]},
    {"desc": "youth skill training", "profile": {"age": 21, "description": "unemployed, want free skill training and a job"}, "expect": ["pmkvy", "ddu-gky"]},
    {"desc": "rooftop solar", "profile": {"description": "own a house, want solar panels to cut my electricity bill"}, "expect": ["pm-surya-ghar"]},
    {"desc": "pregnant woman", "profile": {"gender": "female", "age": 24, "description": "pregnant with first child, want maternity benefit"}, "expect": ["pmmvy"]},
    {"desc": "micro business loan", "profile": {"description": "want a small collateral-free loan to open a shop"}, "expect": ["pm-mudra", "cgtmse"]},
]

K = 5


def _rank(expected: list[str], result_ids: list[str]) -> int | None:
    """1-based rank of the first expected id in result_ids, or None if absent."""
    for i, rid in enumerate(result_ids, start=1):
        if rid in expected:
            return i
    return None


def main() -> None:
    store.ensure_seeded()
    hit1 = hit3 = 0
    reciprocal_sum = 0.0
    print(f"Evaluating {len(CASES)} cases (top-{K} retrieval)\n")
    for case in CASES:
        query = Profile(**case["profile"]).to_query()
        results = store.search(query, K)
        ids = [r["id"] for r in results]
        rank = _rank(case["expect"], ids)
        if rank == 1:
            hit1 += 1
        if rank is not None and rank <= 3:
            hit3 += 1
        if rank is not None:
            reciprocal_sum += 1.0 / rank
        mark = "✓" if (rank and rank <= 3) else "✗"
        got = ids[0] if ids else "-"
        print(f"  {mark} [{case['desc']:24}] rank={rank or '-'}  top={got}")

    n = len(CASES)
    print("\n--- metrics ---")
    print(f"  hit@1 : {hit1}/{n}  ({hit1 / n:.0%})")
    print(f"  hit@3 : {hit3}/{n}  ({hit3 / n:.0%})")
    print(f"  MRR   : {reciprocal_sum / n:.3f}")


if __name__ == "__main__":
    main()
