# Chunk Norris

This is a learning project exploring Retrieval-Augmented Generation in Spring Boot using Spring AI. It exposes a REST API for uploading documents (PDF and markdown), ingesting them through a chunking and embedding pipeline into a pgvector-backed store, and answering natural-language questions grounded in the retrieved content with source citations. The goal is to learn embeddings, vector similarity search, chunking strategies, prompt augmentation, and RAG quality evaluation, while running entirely locally with Ollama and Docker Compose.

## Models & Dimensions

- **Chat Model**: `qwen2.5:7b`
- **Embedding Model**: `nomic-embed-text-v2-moe`
- **Embedding Dimension**: `768` (must match `VECTOR(768)` in `vector_store`)

## Retrieval Configuration & Threshold Tuning

- **Default `topK`**: `4`
- **Default `similarityThreshold`**: `0.50`
- **Tuning Endpoint**: `POST /api/debug/retrieve`
- **Rationale**: Based on empirical testing with `nomic-embed-text-v2-moe`, strong matches score between 0.65 and 0.88, contextual passages score between 0.50 and 0.65, and out-of-domain/noise chunks score below 0.42. The threshold of 0.50 prevents hallucination while capturing relevant context. Full reasoning is documented in [ADR 002](docs/adr/002-retrieval-threshold-and-topk-tuning.md).

p.s.
other names that were considered for this project were
- vectorious
- the embedding room
- ragamuffin
- sir cosine of similarity
- semantica nova
- nearest regards
- dear neighbour, k

i also considered just calling it a 'golden retriever' but i digress. 

