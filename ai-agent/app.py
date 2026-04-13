from fastapi import FastAPI, Header, HTTPException
from pydantic import BaseModel
import os

from rag import search
from google import genai

app = FastAPI()


class QueryRequest(BaseModel):
    prompt: str


class QueryResponse(BaseModel):
    text: str


AI_AGENT_API_KEY = os.getenv("AI_AGENT_API_KEY", "")
GEMINI_API_KEY = os.getenv("GEMINI_API_KEY", "")
GEMINI_MODEL = os.getenv("GEMINI_MODEL", "gemini-2.5-flash")


@app.post("/api/agent/query", response_model=QueryResponse)
def query(request: QueryRequest, x_ai_agent_key: str | None = Header(None)):
    if AI_AGENT_API_KEY and x_ai_agent_key != AI_AGENT_API_KEY:
        raise HTTPException(status_code=401, detail="Invalid or missing API key")

    if not GEMINI_API_KEY:
        raise HTTPException(status_code=500, detail="GEMINI_API_KEY is not set")

    context = search(request.prompt)
    context_text = "\n".join(context)

    prompt = f"""
You are a helpful assistant for DealLock.

Use the context below when possible, but if the context is insufficient,
use your general knowledge to give a helpful answer. If unsure, ask a short
clarifying question instead of saying "I don't know".

Context:
{context_text}

User:
{request.prompt}
"""

    client = genai.Client(api_key=GEMINI_API_KEY)
    response = client.models.generate_content(
        model=GEMINI_MODEL,
        contents=prompt,
    )

    return QueryResponse(text=response.text)
