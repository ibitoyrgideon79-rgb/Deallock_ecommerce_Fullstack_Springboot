import numpy as np
import os
from pathlib import Path
from tempfile import mkdtemp
from doc_chunks import documents
from dotenv import load_dotenv
from pymilvus import MilvusClient, connections
from google import genai
import tqdm

milvus_client= MilvusClient("http://localhost:19530")
load_dotenv()

COLLECTION_NAME = "deal_lock_kb"

# Better model
client = genai.Client(api_key=os.getenv("GEMINI_API_KEY"))

# Prepare text


def build_index():
    texts = [doc["question"] + " " + doc["answer"] for doc in documents]
    response= client.models.embed_content(model = "models/gemini-embedding-001", contents=texts)
    embeddings= response.embeddings
    dim = len(embeddings[0].values)
    milvus_client.create_collection(COLLECTION_NAME, dimension=dim, metric="COSINE")
    rows= [
        {
            "id": i,
            "vector": embeddings[i].values,
            "question": documents[i]["question"],
            "answer": documents[i]["answer"],
        }
        for i in range(len(documents))  
    ]
    milvus_client.insert(COLLECTION_NAME, rows)
    print(f"Indexed {len(rows)} documents into Milvus collection '{COLLECTION_NAME}'.")

existing = milvus_client.list_collections()
if COLLECTION_NAME not in existing:
    print(f"Collection '{COLLECTION_NAME}' not found. Building index...")
    build_index()   
else:
    count= milvus_client.get_collection_stats(COLLECTION_NAME).get("row_count", 0)
    if count == 0:
        milvus_client.drop_collection(COLLECTION_NAME)
        build_index()


def search(query: str, top_k: int = 3) -> list[str]:
    data= client.models.embed_content(model = "models/gemini-embedding-001", contents=[query])
    query_vector= data.embeddings[0].values
    results = milvus_client.search(COLLECTION_NAME, data= [query_vector], limit=top_k, search_params={"metric_type": "COSINE"}, output_fields=[ "answer"])
    return [res["entity"]["answer"] for res in results[0]]
