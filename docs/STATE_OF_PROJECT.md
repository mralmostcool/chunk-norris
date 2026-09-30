# State of the Project

**Last Updated:** 2026-09-30  
**Active Branch:** `dev`  
**Current Version:** `0.4.0-SNAPSHOT`  
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
- [ ] **RAG-023: Checksum and Duplicate Detection** (Next up)
- [ ] **RAG-024: Parsing with TikaDocumentReader** (Next up)
- [ ] **RAG-025: Chunking with TokenTextSplitter**
- [ ] **RAG-026: Embed and Store**
- [ ] **RAG-027: IngestionService Orchestration**
- [ ] **RAG-028: Document Endpoints** (`/api/documents`)
- [ ] **RAG-029: Delete Document API**
- [ ] **RAG-030: Update / Re-ingest Document API**

---

## 3. Test Suite Status
- Total Tests: 51
- Passing: 51
- Failures: 0
- Errors: 0
- Skipped: 0
- Key Test Slices:
  - Unit tests: `FileStorageServiceTest`, `UploadValidatorTest`, `DbHealthIndicatorTest`, `OllamaHealthIndicatorTest`, `CorrelationIdFilterTest`.
  - WebMvc slices: `GlobalExceptionHandlerTest`.
  - Testcontainers / Integration: `DocumentRepositoryIntegrationTest`, `VectorStoreRoundTripIntegrationTest`, `OllamaSmokeIntegrationTest`.

---

## 4. Environment & Configuration
- Upload Directory: `data/uploads/` (configured via `rag.upload-dir`).
- Max Upload Size: `20971520` bytes (20MB) (configured via `rag.max-upload-bytes` and `spring.servlet.multipart.max-file-size`).
- Database: PostgreSQL on port 5432 with Flyway migrations `V1` - `V4`.
- Ollama: URL `http://localhost:11434`, chat model `qwen2.5:7b`, embedding model `nomic-embed-text-v2-moe`.
