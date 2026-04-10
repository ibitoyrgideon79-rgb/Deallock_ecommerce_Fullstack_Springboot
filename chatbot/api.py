from fastapi import FastAPI
from pydantic import BaseModel
from Rag import search
from google import genai

client = genai.Client(api_key="AIzaSyA-AEHpj1H9UjUqQvMLRuyRqY5qYUA2RBs")

app = FastAPI()

class Query(BaseModel):
    message: str


@app.post("/chat")
def chat(query: Query):
    context = search(query.message)
    context_text = "\n".join(context)

    prompt = f"""
    You are a helpful assistant for DealLock.

    Context:
    {context_text}

    User: {query.message}
    """

    response = client.models.generate_content(
        model="gemini-2.5-flash",
        contents=prompt
    )

    return {"response": response.text}