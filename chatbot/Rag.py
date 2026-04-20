import numpy as np
import os
from pathlib import Path
from tempfile import mkdtemp
from doc_chunks import documents
from dotenv import load_dotenv
import faiss
from google import genai
import tqdm
from sklearn.preprocessing import normalize

load_dotenv()


# Better model
client = genai.Client(api_key=os.getenv("GEMINI_API_KEY"))

# Prepare text


def build_index():
    texts = [doc["question"] + " " + doc["answer"] for doc in documents]
    response= client.models.embed_content(model = "models/gemini-embedding-001", contents=texts)
    embeddings= response.embeddings
    # embeddings = normalize(embeddings, norm = "l2")
    dim = len(embeddings[0].values)
    global index
    index= faiss.IndexFlatIP(dim)
    rows= [
        {
            "id": i,
            "vector": embeddings[i].values,
            "question": documents[i]["question"],
            "answer": documents[i]["answer"],
        }
        for i in range(len(documents))  
    ]
    index.add(np.array([row["vector"] for row in rows], dtype=np.float32))
    print(f"Indexed {len(rows)} documents into Faiss index.")


def search(query: str, top_k: int = 3) -> list[str]:
    data= client.models.embed_content(model = "models/gemini-embedding-001", contents=[query])
    query_vector= data.embeddings[0].values
    # query_vector= normalize(query_vector, norm = "l2")
    build_index()
    D, I = index.search(np.array([query_vector], dtype=np.float32), k=top_k)
    return [documents[i]["answer"] for i in I[0]]