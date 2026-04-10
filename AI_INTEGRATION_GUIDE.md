# AI Agent Integration Guide

## Update (April 2026)
The Python agent is no longer a placeholder. It now includes:
- `ai-agent/app.py` with RAG + Gemini integration
- `ai-agent/knowledge_base.py` with the embedded FAQ
- `ai-agent/rag.py` for vector search
- New env vars: `GEMINI_API_KEY` and `GEMINI_MODEL`

Required env:
```env
GEMINI_API_KEY=your-key
GEMINI_MODEL=gemini-2.5-flash
```

Optional auth:
```env
AI_AGENT_API_KEY=your-secret
```


The Java backend is fully wired to accept a Python AI agent microservice. The Python skeleton is in place. now need to write the actual AI logic in `ai-agent/app.py`.**

---

## What's Already Done

### Java Backend (90% Complete JUST NEED TO RUN SOME TESTS,  TOO TIRED TO DO THAT NOW)
- ✅ `AiAgentClient.java` - Service layer that calls the Python endpoint
- ✅ `AiAgentController.java` - REST API endpoint at `POST /api/ai/query`
- ✅ `ai-agent.html` - Web UI at `/ai-agent` for authenticated users
- ✅ `PageController.java` - Route handler for the AI agent page
- ✅ `application.yaml` & `application-prod.yaml` - Config with AI service URL and API key
- ✅ `docker-compose.yml` - Service orchestration for both Java and Python
- ✅ `.env.example` - Environment variables pre-configured

### Python Scaffold (Skeleton Only)
- ✅ `ai-agent/app.py` - FastAPI server with placeholder response
- ✅ `ai-agent/requirements.txt` - Dependencies (fastapi, uvicorn)
- ✅ `ai-agent/Dockerfile` - Container definition
- ✅ `ai-agent/.env.example` - Environment template

---

## What YOU Need to Do

### 1. Write the Python AI Logic
Replace the placeholder in `ai-agent/app.py`:

### 2. Add Dependencies
Update `ai-agent/requirements.txt` with what you need:


### 3. Set Environment Variables
In `.env` (I SET THIS IN DOCKER ENV USING NANO, SO), provide:
```env
AI_AGENT_API_KEY=
# etc. OR SIMPLY USE PROD.YAML FOR LOCAL TESTING.
```
### 4. Test Locally (Without Docker)

**Terminal 1 - Start Java Backend:**
```powershell
.\mvnw.cmd spring-boot:run
```

**Terminal 2 - Start Python AI Agent:**
```powershell
cd ai-agent
pip install -r requirements.txt
$env:AI_AGENT_API_KEY = "your-secret-key"
python -m uvicorn app:app --reload --port 8000
```

**Terminal 3 - Test the Integration:**
```powershell
# Test Python endpoint directly
curl -X POST http://localhost:8000/api/agent/query `
  -H "Content-Type: application/json" `
  -H "X-AI-AGENT-KEY: your-secret-key" `
  -d '{"prompt":"What is 2+2?"}'

# Test via Java backend
curl -X POST http://localhost:8080/api/ai/query `
```
---

## Test with Docker Compose

IF YOU READ THIS, ASK ME FOR VPS LOGINS OR REQUEST I RUN TEST FOR YOU...NNEKA OR RASHEDAT
```powershell
# Build and start everything
docker compose up --build

DONT FORGET TO REPLACE LOCALHOST WITH HTTPS//WWW.DEALLOCK.NG

# View logs for debugging
docker compose logs -f ai-agent
docker compose logs -f app
```

---

## Integration Architecture

```
┌─────────────────────────────────────────────────────┐
│                   User Browser                      │
│          GET /ai-agent (Thymeleaf HTML)             │
│          POST /api/ai/query (JSON)                  │
└────────────────────┬────────────────────────────────┘
                     │
┌────────────────────v────────────────────────────────┐
│              Java Spring Boot App                   │
│   (Port 8080 in Docker / localhost:8080 local)     │
│                                                    │
│  AiAgentController (/api/ai/query)                 │
│  └─ AiAgentClient.ask(prompt)                      │
│     └─ HTTP POST to Python Service                 │
└────────────────────┬────────────────────────────────┘
                     │
      HTTP POST /api/agent/query
   (with X-AI-AGENT-KEY header)
                     │
┌────────────────────v────────────────────────────────┐
│            Python FastAPI Service                   │
│   (Port 8000 in Docker / localhost:8000 local)     │
│                                                    │
│  POST /api/agent/query                             │
│  ├─ Validate API key                               │
│  ├─ Run AI logic             │
│  └─ Return response                                │
└─────────────────────────────────────────────────────┘
```
