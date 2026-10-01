# State of the Project

**Last Updated:** 2026-09-30  
**Active Branch:** `dev`  
**Current Version:** `0.5.0-SNAPSHOT`  
**Java Version:** Java 21 (Spring Boot 4.1.1, Spring AI 2.0.1)

---

## 1. System Architecture & Components

```
Client Requests
      │
      ▼
CorrelationIdFilter (X-Correlation-Id / MDC)
      │
      ▼
[Controllers] ──► [Services / Logic] ──► [Repositories / Database]
                        │
       ┌────────────────┼────────────────┐
       ▼                ▼                ▼
FileStorageService  UploadValidator  DocumentRepository
(./data/uploads)   (Tika 3.3.1 MIME)    (PostgreSQL /
                                        pgvector / Flyway)
```

---

## 2. Milestone Progress

### Milestone 1: Hello Vector & Core Setup ✅
- [x] RAG-010: Docker compose setup (PostgreSQL with `pgvector`, Ollama).
- [x] RAG-011: Smoke integration tests for Ollama chat and embedding models.
- [x] RAG-012: Vector store round trip integration tests with Testcontainers.
- [x] RAG-013: Actuator custom health indicators (`DbHealthIndicator`, `OllamaHealthIndicator`).
- [x] RAG-014: Global exception handling (`GlobalExceptionHandler`) and correlation ID logging (`CorrelationIdFilter`).

### Milestone 2: Document Ingestion (Module 1) ⏳
- [x] **RAG-020: Document Model, DTOs & Repository**
  - Entity: `Document` (id, filename, contentType, sizeBytes, checksum, chunkCount, status, failureReason, uploadedAt).
  - Status enum: `PROCESSING`, `READY`, `FAILED`.
  - Repository: `DocumentRepository` (JdbcTemplate).
  - Testcontainers integration test: `DocumentRepositoryIntegrationTest`.
- [x] **RAG-021: File Storage Component**
  - Service: `FileStorageService` saving raw files to `./data/uploads/{docId}/{filename}`.
  - Path traversal sanitization preventing directory escapement.
  - Safe recursive deletion: `delete(UUID docId)` removes doc directory.
  - Unit tests: `FileStorageServiceTest`.
- [x] **RAG-022: Upload Validation**
  - Component: `UploadValidator`.
  - Allow-list: PDF, DOCX, TXT, HTML (extension + Apache Tika MIME detection).
  - Max size enforcement (throws `MaxUploadSizeExceededException` -> 413).
  - Empty file detection (throws `EmptyFileException` -> 400).
  - Unit tests: `UploadValidatorTest`.
- [x] **RAG-023: Checksum and Duplicate Detection**
  - Service: `ChecksumService` computing SHA-256 hex string from bytes, input streams, and multipart files.
  - Repository: `DocumentRepository.findByChecksumAndStatus(checksum, status)`.
  - Migration: `V5__relax_failed_checksum_unique_constraint.sql` allows duplicate checksum when status is `FAILED`.
  - Duplicate detection rule: 409 `DuplicateDocumentException` on `READY` or active duplicates; re-ingest allowed on `FAILED`.
  - Tests: `ChecksumServiceTest`, `DocumentRepositoryIntegrationTest`.
- [x] **RAG-024: Parsing with TikaDocumentReader**
  - Component: `DocumentParser` wrapping Spring AI `TikaDocumentReader`.
  - Exception: `DocumentParsingException` (422 UNPROCESSABLE_CONTENT) for empty/unparseable files.
  - Fixtures: `sample.pdf`, `sample.docx`, `sample.txt`, `sample.html`.
  - Tests: `DocumentParserTest` verifying all formats parse to non-empty text.
- [x] **RAG-025: Chunking with TokenTextSplitter**
  - Component: `DocumentChunker` using jtokkit CL100K tokenization with `chunkSize` and `chunkOverlap` from `RagProperties`.
  - Metadata enrichment: `docId`, `filename`, `chunkIndex`, `page`.
  - Tests: `DocumentChunkerTest` asserting chunk count, overlap preservation, and metadata keys.
- [x] **RAG-026: Embed and Store**
  - Component: `VectorBatchService` batching chunks to `VectorStore.add()` (default batch 50) to prevent timeouts.
  - Lifecycle: sets `chunkCount` and marks document `READY` on success, `FAILED` with failureReason on error.
  - Tests: `VectorBatchServiceTest` and `VectorBatchServiceIntegrationTest` verifying vector count matches `chunkCount`.
- [x] **RAG-027: IngestionService Orchestration**
  - Component: `IngestionService` orchestrating validate -> hash -> duplicate check -> register -> save -> parse -> chunk -> embed -> READY.
  - Fault compensation: cleans up vector fragments and disk files, sets `FAILED` with failureReason on any error.
  - Tests: `IngestionServiceTest` injecting failures at each stage asserting clean state.
- [x] **RAG-028: Document Endpoints** (`/api/documents`)
  - Controller: `DocumentController` exposing `POST /api/documents` (201), `GET /api/documents` (200), `GET /api/documents/{id}` (200/404).
  - Tests: `DocumentControllerTest` (WebMvc controller slice) and `DocumentIngestionE2EIntegrationTest` (end-to-end small PDF ingestion).
- [x] **RAG-029: Delete Document API**
  - Endpoint: `DELETE /api/documents/{id}` (204 No Content / 404 Not Found).
  - Ordered deletion: deletes vectors (by docId) -> deletes physical files -> deletes database metadata row.
  - Tests: `DocumentDeleteIntegrationTest` confirming complete vector unretrievability and subsequent 404, `DocumentControllerTest`, and `IngestionServiceTest`.
- [x] **RAG-030: Update / Re-ingest Document API**
  - Endpoint: `PUT /api/documents/{id}` (200 OK).
  - Pipeline: file validation -> old vector deletion -> file replacement -> re-parsing -> re-chunking -> re-embedding -> metadata update.
  - Tests: `DocumentUpdateIntegrationTest` verifying old content unretrievable and new content retrievable, `DocumentControllerTest`, and `IngestionServiceTest`.

### Milestone 3: Retrieval (Module 4) ✅
- [x] **RAG-040: Retrieval models and config**
  - Models: `RetrievedChunk` (record with id, text, metadata, score, citationIndex).
  - Config: `RetrievalConfig` (topK, similarityThreshold) bound to `rag.retrieval.*` with defaults (topK=4, similarityThreshold=0.50).
  - Tests: `RetrievedChunkTest`, `RetrievalConfigTest`.
- [x] **RAG-041: Basic RetrievalService**
  - Service: `RetrievalService` executing `vectorStore.similaritySearch` with topK and similarityThreshold.
  - Returns empty list when no matches pass threshold (no weak padding).
  - Tests: `RetrievalServiceTest` and `RetrievalServiceIntegrationTest` (seeded hits vs gibberish query).
- [x] **RAG-042: Metadata filter builder**
  - Component: `MetadataFilterBuilder` constructing `docId IN (...)` `Filter.Expression` from optional UUID collection.
  - Tests: `MetadataFilterBuilderTest` (null, empty, single, multiple, dedup) and `MetadataFilterIntegrationTest` (query isolation).
- [x] **RAG-043: De-duplication of near-identical chunks**
  - Component: `ChunkDeduplicator` dropping normalized-text matches and window overlaps (default threshold 0.70) while preserving highest score.
  - Tests: `ChunkDeduplicatorTest`, `RetrievalServiceTest`.
- [x] **RAG-044: Citation index assignment**
  - Component: `CitationIndexer` assigning contiguous stable `[1]..[N]` citation indices in ranked order after dedup.
  - Tests: `CitationIndexerTest`, `RetrievalServiceTest`.
- [x] **RAG-045: Debug retrieval endpoint & tuning rationale**
  - Controller: `DebugRetrievalController` exposing `POST /api/debug/retrieve` returning chunks with scores.
  - ADR: `docs/adr/002-retrieval-threshold-and-topk-tuning.md` and `README.md` documenting threshold rationale (0.50) and topK (4).
  - Tests: `DebugRetrievalControllerTest` (WebMvc slice) and `DebugRetrievalIntegrationTest` (Testcontainers pgvector).

---

## 3. Test Suite Status
- Total Tests: 127
- Passing: 127
- Failures: 0
- Errors: 0
- Skipped: 0
- Key Test Slices:
  - Unit tests: `FileStorageServiceTest`, `UploadValidatorTest`, `DbHealthIndicatorTest`, `OllamaHealthIndicatorTest`, `CorrelationIdFilterTest`, `RetrievalConfigTest`, `RetrievedChunkTest`, `RetrievalServiceTest`, `MetadataFilterBuilderTest`, `ChunkDeduplicatorTest`, `CitationIndexerTest`.
  - WebMvc slices: `GlobalExceptionHandlerTest`, `DocumentControllerTest`, `DebugRetrievalControllerTest`.
  - Testcontainers / Integration: `DocumentRepositoryIntegrationTest`, `VectorStoreRoundTripIntegrationTest`, `OllamaSmokeIntegrationTest`, `RetrievalServiceIntegrationTest`, `MetadataFilterIntegrationTest`, `DebugRetrievalIntegrationTest`.

---

## 4. Environment & Configuration
- Upload Directory: `data/uploads/` (configured via `rag.upload-dir`).
- Max Upload Size: `20971520` bytes (20MB) (configured via `rag.max-upload-bytes` and `spring.servlet.multipart.max-file-size`).
- Database: PostgreSQL on port 5432 with Flyway migrations `V1` - `V4`.
- Ollama: URL `http://localhost:11434`, chat model `qwen2.5:7b`, embedding model `nomic-embed-text-v2-moe`.
