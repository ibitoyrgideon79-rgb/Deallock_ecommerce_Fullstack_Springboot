from sentence_transformers import SentenceTransformer
import faiss
import numpy as np
from knowledge_base import documents


MODEL_NAME = "all-mpnet-base-v2"
_model = SentenceTransformer(MODEL_NAME)


_texts = [doc["question"] + " " + doc["answer"] for doc in documents]
_embeddings = _model.encode(_texts)
_embeddings = np.array(_embeddings).astype("float32")

_index = faiss.IndexFlatL2(_embeddings.shape[1])
_index.add(_embeddings)


def search(query: str, k: int = 3):
    query_vector = _model.encode([query]).astype("float32")
    distances, indices = _index.search(query_vector, k=k)
    results = []
    for i in indices[0]:
        results.append(documents[i]["answer"])
    return results
