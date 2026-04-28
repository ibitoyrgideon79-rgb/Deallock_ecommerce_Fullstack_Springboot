"""
api.py
------
FastAPI app. Keeps the original /chat endpoint shape and adds:
  - session_id field so conversation history works across turns
  - DELETE /chat/{session_id} to clear history
  - GET /health for Docker healthcheck
  - API key loaded from .env (never hardcoded)
"""
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
from chatbot import get_response, clear_session, MemoryStore

app = FastAPI(title="DealLock Chatbot API")


class Query(BaseModel):
    message:    str
    session_id: str = MemoryStore.generate_session_id(self=MemoryStore())   # pass a user-specific ID to maintain history


# ----------------------------------------------------------------
# POST /chat — same as before, now session-aware
# ----------------------------------------------------------------
@app.post("/chat")
def chat(query: Query):
    if not query.message.strip():
        raise HTTPException(status_code=400, detail="message cannot be empty")

    response = get_response(query.message, session_id=query.session_id)
    return {
        "response":   response,
        "session_id": query.session_id,
    }


# ----------------------------------------------------------------
# DELETE /chat/{session_id} — reset conversation history
# ----------------------------------------------------------------
@app.delete("/chat/{session_id}")
def reset_session(session_id: str):
    clear_session(session_id)
    return {"status": "cleared", "session_id": session_id}


# ----------------------------------------------------------------
# GET /health — Docker / load balancer healthcheck
# ----------------------------------------------------------------
@app.get("/health")
def health():
    return {"status": "ok"}

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="127.0.0.1", port=8000)