# Start the Python GenAI service (http://localhost:8000)
# First run: creates the venv, installs deps. Requires Ollama running.
Set-Location "$PSScriptRoot\ai"
if (-not (Test-Path ".venv")) { python -m venv .venv }
& ".\.venv\Scripts\python.exe" -m pip install -q -r requirements.txt
& ".\.venv\Scripts\python.exe" -m uvicorn main:app --reload --port 8000
