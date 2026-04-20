"""
chatbot.py
----------
In-memory session management using an OrderedDict as an LRU cache.

Behaviour:
  - Max 500 concurrent sessions (oldest evicted when full)
  - Each session keeps the last 10 turns (user + assistant)
  - Sessions expire after 2 hours of inactivity
  - Thread-safe via a single lock (uvicorn runs one process by default)

When you're ready to scale, swap MemoryStore for a Redis-backed
store — the get_response() and clear_session() interface stays identical.
"""
import os
import time
import threading
from collections import OrderedDict
from dotenv import load_dotenv
from google import genai
from Rag import search
import uuid

load_dotenv()

gemini = genai.Client(api_key=os.getenv("GEMINI_API_KEY"))

MAX_HISTORY_TURNS = 10
SESSION_TTL       = 2 * 60 * 60   # 2 hours in seconds
MAX_SESSIONS      = 500


# ----------------------------------------------------------------
# MemoryStore — LRU dict with TTL expiry
# ----------------------------------------------------------------

class MemoryStore:
    """
    Thread-safe in-memory store for conversation histories.
    Uses an OrderedDict so the oldest session is always at the front.
    """

    def __init__(self, max_sessions: int = MAX_SESSIONS, ttl: int = SESSION_TTL):
        self._store:    OrderedDict[str, dict] = OrderedDict()
        self._lock      = threading.Lock()
        self._max       = max_sessions
        self._ttl       = ttl

    def generate_session_id(self) -> str:
        """Generate a random session ID if not provided(using UUIDs)."""
        return self._store.get("session_id", str(uuid.uuid4()))
    
    def get(self, session_id: str) -> list[dict]:
        """Return the message list for a session, or [] if missing/expired."""
        with self._lock:
            entry = self._store.get(session_id)
            if entry is None:
                return []
            if time.time() - entry["last_active"] > self._ttl:
                del self._store[session_id]
                return []
            # Move to end (most recently used)
            self._store.move_to_end(session_id)
            return entry["messages"]

    def save(self, session_id: str, messages: list[dict]) -> None:
        """Persist a message list, evicting the oldest session if at capacity."""
        with self._lock:
            if session_id in self._store:
                self._store.move_to_end(session_id)
            elif len(self._store) >= self._max:
                self._store.popitem(last=False)   # evict oldest
            self._store[session_id] = {
                "messages":    messages,
                "last_active": time.time(),
            }

    def delete(self, session_id: str) -> None:
        with self._lock:
            self._store.pop(session_id, None)

    def size(self) -> int:
        return len(self._store)


_store = MemoryStore()


# ----------------------------------------------------------------
# Helpers
# ----------------------------------------------------------------

def _trim(messages: list[dict]) -> list[dict]:
    """Keep only the last MAX_HISTORY_TURNS user+assistant pairs."""
    max_msgs = MAX_HISTORY_TURNS * 2
    return messages[-max_msgs:] if len(messages) > max_msgs else messages


def _format_history(messages: list[dict]) -> str:
    if not messages:
        return ""
    lines = []
    for m in messages:
        role = "User" if m["role"] == "user" else "Assistant"
        lines.append(f"{role}: {m['text']}")
    return "\n".join(lines)


# ----------------------------------------------------------------
# Public interface
# ----------------------------------------------------------------

def get_response(user_input: str, session_id: str = MemoryStore.generate_session_id(self=MemoryStore())) -> str:
    try:
        # 1. Retrieve relevant chunks from Milvus
        relevant_chunks = search(user_input)
        context         = "\n\n".join(relevant_chunks)

        # 2. Load history from memory store
        messages     = _store.get(session_id)
        history_text = _format_history(messages)
        history_section = (
            f"\nConversation so far:\n{history_text}\n"
            if history_text else ""
        )

        # 3. Build prompt
        prompt = f"""You are a helpful assistant for DealLock, a Nigerian buy-now-pay-later platform.

Answer the question using ONLY the context below.
If the answer is not in the context, say you don't know and direct the user to support@deallock.ng.
Keep answers concise and friendly.
{history_section}
Context:
{context}

User: {user_input}
Assistant:"""


        # generation_config= genai.types.GenerationConfig(
        #         frequency_penalty= 1.5
            
            # ) 
        # 4. Call Gemini
        response = gemini.models.generate_content(
            model    = "gemini-2.5-flash",
            contents = prompt
            # generation_config = generation_config  # discourage copy-pasting from context
        )
        reply = response.text.strip()

        # 5. Update history in memory store
        messages.append({"role": "user",      "text": user_input})
        messages.append({"role": "assistant", "text": reply})
        _store.save(session_id, _trim(messages))

        return reply

    except Exception as e:
        print("Error:", e)
        return "Sorry, I'm having trouble right now. Please try again later."


def clear_session(session_id: str) -> None:
    """Manually clear a session's history."""
    _store.delete(session_id)


# ----------------------------------------------------------------
# Local testing
# ----------------------------------------------------------------
if __name__ == "__main__":
    session = "local-test"
    print("DealBot ready. Type 'quit' to exit, 'clear' to reset history.\n")
    while True:
        user_input = input("You: ").strip()
        if not user_input:
            continue
        if user_input.lower() == "quit":
            break
        if user_input.lower() == "clear":
            clear_session(session)
            print("History cleared.\n")
            continue
        print(f"Bot: {get_response(user_input, session_id=session)}\n")