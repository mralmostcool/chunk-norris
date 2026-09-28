CREATE TABLE IF NOT EXISTS vector_store (
    id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    content   TEXT,
    metadata  JSONB,
    embedding VECTOR(768)          -- MUST equal the embedding model's output size
);

CREATE INDEX IF NOT EXISTS idx_vector_store_embedding
    ON vector_store USING hnsw (embedding vector_cosine_ops);

-- Speeds up "delete/filter by document id" (RAG-029, RAG-042)
CREATE INDEX IF NOT EXISTS idx_vector_store_doc_id
    ON vector_store ((metadata ->> 'docId'));