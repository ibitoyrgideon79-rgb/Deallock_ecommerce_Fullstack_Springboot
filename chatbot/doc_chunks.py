"""
knowledge_base.py
-----------------
Parses knowledge_base.md into a list of {"question": ..., "answer": ...} dicts.

Each ## heading becomes the question; the text below it becomes the answer.
This means knowledge_base.md is the single source of truth —
no duplicate data to keep in sync.
"""
from pathlib import Path

_MD_PATH = Path(__file__).parent / "knowledge_base.md"


def _parse_markdown(path: Path) -> list[dict]:
    text    = path.read_text(encoding="utf-8")
    chunks  = []
    current_heading = None
    current_body    = []

    for line in text.splitlines():
        if line.startswith("## "):
            # Save previous chunk
            if current_heading and current_body:
                chunks.append({
                    "question": current_heading,
                    "answer":   " ".join(current_body).strip(),
                })
            current_heading = line.lstrip("# ").strip()
            current_body    = []
        elif current_heading:
            stripped = line.strip()
            if stripped:
                current_body.append(stripped)

    # Don't forget the last chunk
    if current_heading and current_body:
        chunks.append({
            "question": current_heading,
            "answer":   " ".join(current_body).strip(),
        })

    return chunks

documents = _parse_markdown(_MD_PATH)


if __name__ == "__main__":
    for i, doc in enumerate(documents):
        print(f"[{i}] Q: {doc['question']}")
        print(f"     A: {doc['answer'][:80]}...")
        print()