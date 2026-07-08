"""Build the vector index from data/schemes.json.

Run once (with Ollama running) before starting the API:
    python seed.py
Re-running is safe — it rebuilds from scratch.
"""
import store

if __name__ == "__main__":
    print("Building scheme vector index (embedding via Ollama)...")
    n = store.build_index()
    print(f"Done. Indexed {n} schemes into the Chroma collection.")
