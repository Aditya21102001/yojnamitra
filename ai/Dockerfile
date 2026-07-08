# YojanaMitra GenAI service
FROM python:3.13-slim

WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY . .

# The service needs Ollama reachable at OLLAMA_HOST. In Docker, the host machine's
# Ollama is host.docker.internal; override for a remote Ollama.
ENV OLLAMA_HOST=http://host.docker.internal:11434
EXPOSE 8000
CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8000"]
