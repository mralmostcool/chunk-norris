# ADR 002: Retrieval similarity threshold and topK baseline

Status: accepted

Decision: Set baseline retrieval configuration to `topK = 4` and `similarityThreshold = 0.50` (overridable via `rag.retrieval.top-k` and `rag.retrieval.similarity-threshold` or per-request on `/api/debug/retrieve`).

Why:
- The embedding model `nomic-embed-text-v2-moe` outputs 768-dimensional embeddings, evaluated via cosine distance in pgvector (`score = 1 - cosine_distance`).
- Empirical validation using `POST /api/debug/retrieve`:
  - Highly relevant document chunks score between 0.65 and 0.88.
  - Moderately relevant contextual chunks score between 0.50 and 0.65.
  - Irrelevant or noise chunks and unrelated queries score below 0.42.
- A threshold of 0.50 reliably rejects out-of-domain queries and hallucination-inducing noise while retaining necessary context.
- With chunk size of 300 tokens and overlap of 50 tokens, `topK = 4` feeds at most ~1200 tokens into generation prompt context, leaving ample headroom within model context limits (e.g. 8192 tokens for `qwen2.5:7b`).

Consequences: Queries with no chunks scoring >= 0.50 return an empty list immediately, triggering the generation fallback path without unnecessary LLM calls.
