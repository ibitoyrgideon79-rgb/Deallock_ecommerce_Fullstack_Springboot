from sentence_transformers import SentenceTransformer
import faiss
import numpy as np
from knowledge_base import documents

# Better model
model = SentenceTransformer("all-mpnet-base-v2")

# Prepare text
texts = [doc["question"] + " " + doc["answer"] for doc in documents]

# Create embeddings
embeddings = model.encode(texts)

# Convert to numpy
embeddings = np.array(embeddings).astype("float32")

# Build FAISS index
index = faiss.IndexFlatL2(embeddings.shape[1])
index.add(embeddings)


def search(query):
    query_vector = model.encode([query]).astype("float32")

    distances, indices = index.search(query_vector, k=3)

    results = []
    for i in indices[0]:
        results.append(documents[i]["answer"])

    return results