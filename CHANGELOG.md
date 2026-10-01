# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.6.0-SNAPSHOT] - 2026-10-01

### Added
- `RetrievedChunk` and `RetrievalConfig` configuration models bound to `rag.retrieval.*` (RAG-040).
- `RetrievalService` for vector similarity search with threshold filtering (RAG-041).
- `MetadataFilterBuilder` for building `docId IN (...)` filter expressions (RAG-042).
- `ChunkDeduplicator` dropping normalized-text matches and window overlaps while preserving highest score (RAG-043).
- `CitationIndexer` assigning contiguous stable `[1]..[N]` citation indices in ranked order after dedup (RAG-044).
- `DebugRetrievalController` exposing `POST /api/debug/retrieve` (RAG-045).
- `ADR 002`: Retrieval similarity threshold and topK baseline tuning rationale.

## [0.5.0-SNAPSHOT] - 2026-09-30

### Added
- `ChecksumService` for SHA-256 duplicate detection and relaxed unique constraint for FAILED documents (RAG-023).
- `DocumentParser` wrapping `TikaDocumentReader` for parsing PDF, DOCX, TXT, and HTML files (RAG-024).
- `DocumentChunker` providing sliding-window token chunking with overlap from `RagProperties` and chunk metadata (RAG-025).
- `VectorBatchService` batching chunk embeddings into pgvector with status lifecycle management (RAG-026).
- `IngestionService` orchestrating the complete ingestion pipeline with automatic failure compensation (RAG-027).
- `DocumentController` exposing REST endpoints for document upload, listing, and inspection (RAG-028).
- Ordered document deletion endpoint `DELETE /api/documents/{id}` (RAG-029).
- Document re-ingestion and update endpoint `PUT /api/documents/{id}` (RAG-030).
- `FileStorageService` for raw upload persistence under `./data/uploads/{docId}/{filename}`, path traversal sanitization, and docId-scoped deletion (RAG-021).
- `UploadValidator` for file validation: allow-list (PDF, DOCX, TXT, HTML), Apache Tika MIME detection, max size enforcement, and empty file check (RAG-022).
- Exceptions `EmptyFileException`, `InvalidFilenameException`, and `FileStorageException`.
- Unit tests `FileStorageServiceTest` and `UploadValidatorTest`.
- Document model, repository, and DTOs (RAG-020).

- Actuator health indicators for Ollama and PostgreSQL (RAG-013).
- `OllamaHealthIndicator` verifying connectivity and presence of configured chat and embedding models.
- `DbHealthIndicator` verifying database connectivity and `pgvector` extension installation.
- Unit tests `DbHealthIndicatorTest` and `OllamaHealthIndicatorTest` covering UP, DOWN, missing extension, and missing model scenarios.
- `maven-surefire-plugin` configuration with JVM flag `-XX:+EnableDynamicAgentLoading` to suppress JDK 21+ Mockito agent warnings.

### Changed
- Configured default `rag:` properties block in base `application.yaml`.
- Moved Spring AI Ollama and vectorstore configurations from `application-local.yml` to base `application.yaml`.


## [0.3.0-SNAPSHOT] - 2026-09-29

### Added
- `OllamaSmokeIntegrationTest` smoke integration test verifying Ollama chat and embedding connectivity and dimension match against database column (RAG-011).
- Flyway migration scripts `V1` through `V4` for pgvector extension, documents table, vector_store table, and chat tables.
- Documented model names and embedding dimensions in `README.md`.

### Fixed
- Validation exception import and compiler warning cleanups.

## [0.2.0-SNAPSHOT] - 2026-09-28

### Added
- `CorrelationIdFilter` to manage request correlation IDs via HTTP header `X-Correlation-Id` and SLF4J MDC (`correlationId`).
- `CorrelationIdFilterTest` verifying correlation ID generation, propagation, validation, and MDC cleanup.
- Correlation ID propagation in `ErrorResponse` and corresponding assertions in `GlobalExceptionHandlerTest`.
- `spring-boot-starter-webmvc-test` dependency for WebMVC test slice support in Spring Boot 4.
