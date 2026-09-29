# Chunk Norris

This is a learning project exploring Retrieval-Augmented Generation in Spring Boot using Spring AI. It exposes a REST API for uploading documents (PDF and markdown), ingesting them through a chunking and embedding pipeline into a pgvector-backed store, and answering natural-language questions grounded in the retrieved content with source citations. The goal is to learn embeddings, vector similarity search, chunking strategies, prompt augmentation, and RAG quality evaluation, while running entirely locally with Ollama and Docker Compose.

## Models & Dimensions

- **Chat Model**: `qwen2.5:7b`
- **Embedding Model**: `nomic-embed-text-v2-moe`
- **Embedding Dimension**: `768` (must match `VECTOR(768)` in `vector_store`)


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

