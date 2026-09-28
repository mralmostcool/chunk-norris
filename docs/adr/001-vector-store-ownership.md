# ADR 001: Flyway owns the vector_store table

Status: accepted

Decision: The vector_store table is created by Flyway (V3), and Spring AI schema
initialization is disabled (`initialize-schema: false`).

Why: One source of truth for schema, explicit embedding dimension, explicit index type.

Consequences: `spring.ai.vectorstore.pgvector.dimensions` and the VECTOR(n) column must
be changed together. Changing the embedding model to one with a different output size
requires a new migration and re-embedding all documents.