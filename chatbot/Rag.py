from sentence_transformers import SentenceTransformer
from sklearn.metrics.pairwise import cosine_similarity
import numpy as np
from knowledge_base import documents

# Load model
model = SentenceTransformer("all-MiniLM-L6-v2")

# Prepare embeddings once
questions = [doc["question"] for doc in documents]
embeddings = model.encode(questions)

def search(query):
    query_embedding = model.encode([query])

    scores = cosine_similarity(query_embedding, embeddings)[0]

    best_idx = np.argmax(scores)
    best_score = scores[best_idx]

    print("DEBUG SCORE:", best_score)

    if best_score < 0.20:
        return ["I don't have enough information on that yet."]

    return [documents[best_idx]["answer"]]