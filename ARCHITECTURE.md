# Conversational RAG Service: Project Plan

Stack: Spring Boot, Spring AI, PostgreSQL 16 + pgvector, Ollama (`qwen2.5:7b`, `nomic-embed-text-v2-moe`), Apache Tika, JdbcTemplate.

---

## Part 1: Objectives

### Primary objectives
1. **Vector DB connectivity and maintenance**: connect to the existing pgvector instance, and keep vectors in sync with document lifecycle (add, update, delete).
2. **RAG pipeline**: embed a question, retrieve relevant chunks, and generate an answer grounded only in those chunks, with citations.
3. **Document ingestion**: accept uploads (PDF, DOCX, TXT, HTML), parse, chunk, embed, store; detect duplicates; delete cleanly.
4. **Conversational layer**: multi-turn chat with a sliding-window memory, plus query rewriting so follow-ups like "what about its cost?" become standalone searches.

### Secondary objectives
5. **Evaluation harness**: measure retrieval and answer quality against a golden Q&A set so you can tune chunk size, topK and threshold with evidence rather than guesswork.
6. **Operability**: health checks, structured errors, logging with correlation IDs.

### Non-goals (for v1)
Authentication, multi-tenancy, streaming responses, re-ranking models, UI. These are easy to add later if the layering is kept clean.

### Definition of done for the whole project
- Upload a document, ask a question about it, get a cited answer.
- Ask a follow-up using a pronoun and get a correct answer.
- Ask an out-of-scope question and get a refusal, not a hallucination.
- Delete the document and confirm its vectors, file and metadata are all gone.
- `/api/eval/run` returns a report with hit-rate, groundedness and refusal accuracy.

---

## Part 2: Modules

| # | Module | Responsibility | Depends on |
|---|--------|----------------|-----------|
| 1 | Document Ingestion | Upload, parse, chunk, embed, store, delete documents | Framework (VectorStore), DB, Disk |
| 2 | Conversation & Session | Sessions, message history, sliding-window memory, orchestrates the RAG flow | Modules 3, 4, 5 |
| 3 | Query Rewriting | Turn a follow-up into a standalone query | ChatClient |
| 4 | Retrieval | Similarity search with threshold, filters, dedup, citation indexing | VectorStore |
| 5 | Prompt Engine & Generation | Build grounded prompt, call LLM, extract citations | ChatClient |
| 6 | Evaluation | Golden-set runner and metrics | Module 2 |
| 7 | Health & Ops | Ollama and DB health indicators | Actuator |
| X | Config & Common | Properties, exception handling, logging | All |

Layering inside each module (from your diagram): **Controller → Service → Logic → Repository → Model**. Keep controllers thin, put business rules in services, put pure functions (splitters, prompt builders, trimmers, filter builders) in the logic layer so they are unit-testable without Spring.

Suggested package layout:

```
com.yourorg.rag
├── ingestion      (controller, service, logic, repository, model)
├── conversation   (controller, service, logic, repository, model)
├── rewrite        (service, logic, model)
├── retrieval      (service, logic, model)
├── generation     (service, logic, model)
├── eval           (controller, service, logic, model)
├── health         (OllamaHealthIndicator, DbHealthIndicator)
├── config         (properties, CORS, beans)
└── common         (exceptions, handler, logging filter)
```

---

## Part 3: Endpoints

### Ingestion
| Method | Path | Purpose | Success | Errors |
|--------|------|---------|---------|--------|
| POST | `/api/documents` | Multipart upload; triggers ingest | 201 `DocumentResponse` | 400 bad type, 409 duplicate, 413 too large, 503 Ollama down |
| GET | `/api/documents` | List documents | 200 `List<DocumentResponse>` | |
| GET | `/api/documents/{id}` | Get one document's metadata | 200 | 404 |
| DELETE | `/api/documents/{id}` | Delete vectors + file + metadata | 204 | 404 |

### Conversation
| Method | Path | Purpose | Success | Errors |
|--------|------|---------|---------|--------|
| POST | `/api/chat/conversations` | Create session | 201 `CreateSessionResponse` | |
| POST | `/api/chat/conversations/{id}/messages` | Send message, get answer | 200 `ChatResponse` | 400 blank, 404 session, 503 LLM |
| GET | `/api/chat/conversations/{id}/history` | Fetch messages | 200 | 404 |
| DELETE | `/api/chat/conversations/{id}` | Clear session | 204 | 404 |

### Evaluation
| Method | Path | Purpose | Success |
|--------|------|---------|---------|
| POST | `/api/eval/run` | Run golden set, return report | 200 `EvalReport` |

### Operations
| Method | Path | Purpose |
|--------|------|---------|
| GET | `/actuator/health` | Aggregated UP/DOWN with Ollama and DB details |

Optional but useful during development: `POST /api/debug/retrieve` (query in, raw chunks with scores out). It is the fastest way to tune threshold and topK before the full chat flow exists. Remove or profile-gate it later.

---

## Part 4: Tickets

Conventions used below:
- **Epic** = a module or phase. **Story/Task** = one deliverable, roughly half a day to two days.
- Each ticket has **Acceptance Criteria (AC)**. A ticket is done when every AC passes.
- Ticket IDs are `RAG-<number>`. Do them roughly in order; dependencies are noted.
- Every ticket implicitly includes: unit tests for the logic you added, and no regression in earlier tests.

---

### EPIC 0: Project Foundation

**RAG-001: Bootstrap the Spring Boot project**
- Create the project with Java 21 (or 17), Maven or Gradle.
- Dependencies: web, validation, actuator, jdbc, postgresql driver, Spring AI (ollama starter, pgvector store starter, tika document reader), lombok (optional), test starters.
- Pin the Spring AI BOM version.
- AC: app starts on port 8080; `/actuator/health` returns UP with no other config.

**RAG-002: Establish package structure and layering rules**
- Create the packages from Part 2.
- Add a short `CONTRIBUTING.md` line: controllers never call repositories directly.
- AC: empty packages committed; a sample ArchUnit test (optional) enforces the rule.

**RAG-003: Externalize configuration**
- `application.yml` with Ollama base URL, chat model, embedding model, datasource, and pgvector settings (dimensions, index type, distance type).
- Custom `@ConfigurationProperties` classes: `RagProperties` (chunkSize, chunkOverlap, topK, similarityThreshold, memoryWindow, maxUploadBytes, uploadDir).
- Add `application-local.yml` for your machine.
- AC: all values injectable; changing topK in yml changes behavior without code changes.

**RAG-004: Docker Compose and local runbook**
- Document (or codify) how the existing pgvector container and Ollama are started; add the `docker-compose.yml` for Postgres if not already committed.
- README section: prerequisites, `ollama pull` commands, how to run.
- AC: a fresh clone plus README gets a teammate running in under 15 minutes.

**RAG-005: Global exception handling and error contract**
- `@ControllerAdvice` returning a consistent error body `{timestamp, status, code, message, path, correlationId}`.
- Custom exceptions: `DocumentNotFoundException`, `SessionNotFoundException`, `LlmUnavailableException`, `UnsupportedFileTypeException`, `DuplicateDocumentException`.
- Map to 404 / 404 / 503 / 400 / 409.
- AC: each exception has a controller-slice test asserting status and body shape.

**RAG-006: Correlation ID and request logging filter**
- Servlet filter generating or propagating `X-Correlation-Id`, placing it in MDC; log pattern includes it.
- AC: every log line for a request shares the same ID; the ID is returned in the response header.

---

### EPIC 1: Infrastructure Connectivity (Vector DB + Ollama)

**RAG-010: Database schema migrations**
- Add Flyway (or Liquibase). Migrations for: `CREATE EXTENSION IF NOT EXISTS vector`, `documents`, `chat_sessions`, `chat_messages`.
- Let Spring AI manage `vector_store`, or create it via migration and disable auto-init (pick one and document it).
- Indexes: `documents(checksum)` unique, `chat_messages(conversation_id, created_at)`.
- AC: an empty database becomes fully usable by starting the app; migrations are idempotent.

**RAG-011: Verify Ollama connectivity and models**
- Small smoke test (a runner or an integration test) that calls the chat model with "say ok" and the embedding model with a short string.
- Confirm the embedding dimension matches the `vector_store` column dimension. This is the most common setup bug, so record the number in the README.
- AC: test passes against your local Ollama; a mismatch produces a clear failure message.

**RAG-012: Verify VectorStore round trip**
- Integration test: add 3 tiny documents with metadata, run `similaritySearch`, assert the expected one ranks first, then delete by filter and assert gone.
- Use Testcontainers (pgvector image) so it does not depend on your dev DB.
- AC: passes in CI-style isolation; teaches you the `Document`, `SearchRequest` and `Filter.Expression` APIs before you build on them.

**RAG-013: Health indicators (Module 7)**
- `OllamaHealthIndicator`: ping Ollama, verify both configured models are present.
- `DbHealthIndicator`: connectivity plus `pgvector` extension check.
- AC: stopping Ollama flips `/actuator/health` to DOWN with a useful detail message; same for stopping Postgres.

---

### EPIC 2: Document Ingestion (Module 1)

**RAG-020: Document model, DTOs and repository**
- `Document` entity fields: id (UUID), filename, contentType, sizeBytes, checksum, chunkCount, uploadedAt, status (`PROCESSING`, `READY`, `FAILED`).
- `DocumentRepository` with JdbcTemplate: `insert`, `findAll`, `findById`, `findByChecksum`, `updateStatus`, `updateChunkCount`, `deleteById`.
- DTOs: `DocumentResponse`, `DocumentUploadRequest`.
- AC: repository integration tests (Testcontainers) cover every method.

**RAG-021: File storage component**
- Save raw uploads to `./data/uploads/{docId}/{filename}`; delete by docId.
- Sanitize filenames (prevent path traversal).
- AC: unit tests for path sanitization; deleting a doc removes its directory.

**RAG-022: Upload validation**
- Allow-list of types: PDF, DOCX, TXT, HTML (check both extension and detected MIME type via Tika, not just the header).
- Enforce max size; configure multipart limits in yml.
- AC: wrong type returns 400, oversized returns 413, empty file returns 400.

**RAG-023: Checksum and duplicate detection**
- SHA-256 of file bytes; reject with 409 if the checksum already exists in `READY` state.
- Decide and document the behavior for a duplicate of a `FAILED` doc (recommended: allow re-ingest).
- AC: uploading the same file twice yields 201 then 409.

**RAG-024: Parsing with TikaDocumentReader**
- Wrap Tika in a `DocumentParser` logic class returning text plus metadata.
- Handle empty or unparseable files with a clear exception (mark doc `FAILED`).
- AC: fixture PDF, DOCX, TXT and HTML files each parse to non-empty text in tests.

**RAG-025: Chunking with TokenTextSplitter**
- Configure chunk size and overlap from `RagProperties`.
- Attach metadata to every chunk: `docId`, `filename`, `chunkIndex`, `page` (when available).
- AC: unit test asserts chunk count for a known input, overlap is respected, and every chunk carries all four metadata keys.

**RAG-026: Embed and store**
- Push chunks into `VectorStore.add()` (embedding happens through the `EmbeddingModel`).
- Batch large documents to avoid Ollama timeouts.
- Update `chunkCount` and set status `READY` on success, `FAILED` (with reason) on error.
- AC: after upload, `SELECT count(*) FROM vector_store WHERE metadata->>'docId' = ?` equals `chunkCount`.

**RAG-027: IngestionService orchestration**
- Flow: validate → checksum → register (`PROCESSING`) → save file → parse → chunk → embed/store → mark `READY`.
- On any failure after registration, compensate: remove partial vectors and file, mark `FAILED`.
- Decide sync vs async. For v1, sync is fine; note that large PDFs will hold the request open (an async ticket is in the backlog).
- AC: failure injected at each stage leaves no orphan vectors or files.

**RAG-028: Document endpoints**
- `POST /api/documents`, `GET /api/documents`, `GET /api/documents/{id}`.
- AC: controller-slice tests plus one end-to-end test uploading a real small PDF.

**RAG-029: Delete document**
- `DELETE /api/documents/{id}` removes vectors (filter by `docId` metadata), the file, then the metadata row.
- Order matters: delete vectors first, row last, so a partial failure is retryable.
- AC: after delete, similarity search for that document's content returns nothing; a second delete returns 404.

**RAG-030: Update/re-ingest a document (covers your "updation" objective)**
- `PUT /api/documents/{id}` (or delete-then-upload): replaces file, deletes old vectors, re-chunks and re-embeds.
- AC: after update, old content is no longer retrievable and new content is.

---

### EPIC 3: Retrieval (Module 4)

**RAG-040: Retrieval models and config**
- `RetrievedChunk`, `RetrievalConfig` (topK, similarityThreshold) bound to properties.
- AC: config visible and overridable per environment.

**RAG-041: Basic RetrievalService**
- `retrieve(query, docFilters)` using `VectorStore.similaritySearch` with topK and threshold.
- Return an empty list when nothing passes the threshold (do not pad with weak matches).
- AC: integration test with seeded chunks: relevant query returns hits, gibberish query returns empty.

**RAG-042: Metadata filter builder**
- Logic class producing a `docId IN (...)` filter expression from optional document ids.
- AC: unit tests for null, empty, single and multiple ids; integration test proving filtering restricts results.

**RAG-043: De-duplication of near-identical chunks**
- Drop chunks whose text is near-identical (normalized-text hash, or overlap ratio above a threshold). Overlapping chunks from adjacent windows are the usual culprit.
- AC: unit test with overlapping chunks yields a reduced list preserving highest score.

**RAG-044: Citation index assignment**
- Assign `[1]..[N]` in ranked order after dedup.
- AC: indexes are contiguous and stable for a given result list.

**RAG-045: Debug retrieval endpoint (temporary)**
- `POST /api/debug/retrieve` returning chunks and scores.
- Use it to tune `topK` and `similarityThreshold` on your real documents and record the chosen values with reasoning.
- AC: documented threshold rationale in the README or an ADR.

---

### EPIC 4: Prompt Engine and Generation (Module 5)

**RAG-050: Prompt models**
- `PromptBundle` (system, history, context, question) and `GeneratedAnswer` (text, citedIndexes).
- AC: immutable records with tests for construction.

**RAG-051: ConversationalPromptEngine**
- System prompt with grounding rules: answer only from context; cite as `[n]`; say you do not know when context is insufficient.
- Blocks: history, numbered context, question.
- Store the prompt text in a resource file (`prompts/system.st`), not in Java strings.
- AC: golden-string unit tests on the assembled prompt for a fixed input.

**RAG-052: AnswerGenerationService**
- Call `ChatClient.prompt()...call()`.
- Wrap Ollama failures in `LlmUnavailableException`; set timeouts.
- AC: mocked ChatClient tests for success, timeout and connection failure.

**RAG-053: Citation extraction**
- Parse `[n]` markers from the answer; discard indexes that do not exist in the supplied context (model hallucinated citations).
- AC: unit tests: valid citations, out-of-range citation dropped, no citations returns an empty list.

**RAG-054: Token budget guard**
- Estimate tokens for system + history + context + question; trim context (lowest score first) then history if over budget for the model's context window.
- AC: an oversized input is trimmed rather than failing; test asserts what got dropped.

---

### EPIC 5: Query Rewriting (Module 3)

**RAG-060: Rewrite models and prompt template**
- `RewriteRequest`, `RewriteResult {standaloneQuery, wasRewritten}`.
- Prompt template: resolve pronouns using recent history, output only the search query. Keep it in a resource file.
- AC: template renders correctly for a sample history.

**RAG-061: QueryRewriterService**
- Skip when history is empty (`wasRewritten=false`).
- Call the LLM otherwise; fall back to the original question on failure.
- AC: tests for empty history, success, and LLM failure fallback.

**RAG-062: Output sanitizer**
- Strip surrounding quotes, "Standalone question:" style preambles, and trailing explanations; cap length.
- AC: unit tests with messy model outputs.

**RAG-063: Rewrite quality check**
- Manually try 10 follow-up pairs ("What about its price?" after a question about a product) and record before/after in a doc. This sets your expectation for how well a 7B model rewrites, and feeds the eval set later.
- AC: results table committed; obvious failures inform prompt tweaks.

---

### EPIC 6: Conversation and Memory (Module 2)

**RAG-070: Session models and repository**
- `ChatSession`, `ChatMessage`, `Role` enum; DTOs `CreateSessionResponse`, `ChatRequest`, `ChatResponse {conversationId, answer, sources, historySnippet}`.
- `ChatSessionRepository`: `createSession`, `findSession`, `deleteSession`, `insertMessage`, `findRecentMessages`, `touchLastActive`.
- AC: repository integration tests; deleting a session cascades to its messages.

**RAG-071: Window trimming logic**
- Keep the last N messages (default 6), preserve user/assistant pairing (never start the window with an orphan assistant message), apply the token guard.
- AC: unit tests for odd counts, fewer than N, and pairing edge cases.

**RAG-072: ChatMemoryService**
- `getConversationHistory(id)`, `appendMessagePair(id, user, assistant)`, `clearConversation(id)`.
- Append the pair only after a successful answer, so failures do not leave half-turns.
- AC: tests including the failure path.

**RAG-073: ConversationalRagService (core orchestration)**
- The five steps: load memory → rewrite → retrieve → generate → save.
- Refusal path: if retrieval returns empty, return a fixed "I could not find this in the documents" answer without calling the LLM, and still save the turn.
- Populate `sources` from the cited chunks (filename, page, snippet).
- AC: service test with all collaborators mocked covering normal, empty history, empty retrieval, and LLM failure.

**RAG-074: Session endpoints**
- `POST /api/chat/conversations`, `GET .../history`, `DELETE ...`.
- AC: controller-slice tests including 404 for unknown ids.

**RAG-075: Message endpoint**
- `POST /api/chat/conversations/{id}/messages` with validation (non-blank, max length).
- AC: end-to-end test: upload doc → create session → ask → cited answer; then a pronoun follow-up returns a sensible answer.

**RAG-076: Optional Spring AI ChatMemory integration**
- Your diagram marks this "optional". Evaluate whether Spring AI's advisor-based memory is worth adopting versus your own service. Record the decision as a short ADR. Keeping your own gives you control of the pairing and token rules, which is why it is the default recommendation.
- AC: ADR committed.

---

### EPIC 7: Evaluation Harness (Module 6)

**RAG-080: Golden dataset**
- Create a JSON/YAML file of 20 to 30 cases: `question`, `expectedDocs`, `expectedAnswer` (short key facts), plus 5 to 8 out-of-scope questions flagged `shouldRefuse=true`.
- Build it from documents you have actually ingested.
- AC: dataset committed and loadable.

**RAG-081: Metric calculators**
- Retrieval hit-rate / recall@k: did an expected doc appear in the retrieved set?
- Groundedness / citation check: does every citation index exist, and does the answer contain at least one?
- Refusal accuracy: refused when it should, answered when it should.
- Answer similarity: start simple (key-fact containment or embedding cosine against the expected answer); avoid LLM-as-judge in v1.
- AC: unit tests for each metric using hand-built inputs.

**RAG-082: EvalService and endpoint**
- Run each case through `ConversationalRagService` (fresh session per case), capture latency, aggregate into `EvalReport` (totals, averages, failures list).
- `POST /api/eval/run`.
- AC: a run over the golden set returns a report; you can compare before/after changing topK.

**RAG-083: Baseline and tuning log**
- Record the baseline scores, then change one parameter at a time (chunk size, overlap, topK, threshold) and log the effect.
- AC: tuning table committed; chosen defaults justified by numbers.

---

### EPIC 8: Hardening and Delivery

**RAG-090: Test coverage pass**
- Ensure logic classes have unit tests, repositories have Testcontainers tests, controllers have slice tests, and there is at least one full-flow test.
- AC: coverage report generated; gaps in critical paths closed.

**RAG-091: Input hardening**
- Bound message length, sanitize filenames and metadata, guard against prompt-injection text in documents by keeping the system prompt authoritative and delimiting context clearly.
- AC: a document containing "ignore previous instructions" does not change the system behavior in a manual test.

**RAG-092: Observability**
- Request metrics (timers around retrieval, generation, ingestion) via Micrometer; expose in actuator.
- AC: metrics visible for stage latencies.

**RAG-093: API documentation**
- Add springdoc-openapi; annotate DTOs.
- AC: Swagger UI lists every endpoint with example payloads.

**RAG-094: CORS and multipart limits**
- Configure allowed origins and upload limits from properties.
- AC: verified from a browser client or curl with preflight.

**RAG-095: Packaging**
- Dockerfile for the app, compose file joining app + Postgres, README run instructions.
- AC: `docker compose up` brings the whole stack up (Ollama can stay on the host).

---

### Backlog (post v1)

- **RAG-B01**: Async ingestion with a job status endpoint (`GET /api/documents/{id}/status`).
- **RAG-B02**: Streaming answers (SSE) from the message endpoint.
- **RAG-B03**: Hybrid search (keyword + vector) and re-ranking.
- **RAG-B04**: Per-document or per-user scoping and authentication.
- **RAG-B05**: Conversation summarization for long chats (beyond the sliding window).
- **RAG-B06**: OCR for scanned PDFs.

---

## Part 5: Suggested Order and Milestones

| Milestone | Tickets | What you can demo |
|-----------|---------|-------------------|
| M0: Skeleton | 001 to 006 | App runs, errors are consistent |
| M1: Plumbing | 010 to 013 | DB schema, Ollama and vector round trip proven, health checks |
| M2: Ingestion | 020 to 030 | Upload, list, delete, update documents |
| M3: Retrieval | 040 to 045 | Debug endpoint returns tuned, cited chunks |
| M4: Single-turn RAG | 050 to 054 | Grounded answers with citations (call the services from a test or temporary endpoint) |
| M5: Conversation | 060 to 076 | Multi-turn chat with rewriting and memory |
| M6: Evaluation | 080 to 083 | Scored report and tuned defaults |
| M7: Delivery | 090 to 095 | Documented, hardened, containerized |

Note: M4 can be reached before Epic 5 and 6 by wiring Retrieval → Prompt → Generation directly. That gives you a working (non-conversational) RAG early, which is the most motivating checkpoint in the project.

---

## Part 6: Common Pitfalls to Watch For

1. **Embedding dimension mismatch** between the model output and the `vector_store` column. Check in RAG-011.
2. **Threshold too strict or too loose**: score meaning depends on the distance metric. Tune with RAG-045, not by guessing.
3. **Orphan vectors** when ingestion fails midway. RAG-027 and RAG-029 exist to prevent this.
4. **Chunk overlap producing duplicate context**: RAG-043.
5. **Small model rewriting badly**: it may invent details. Keep the fallback, sanitizer, and rewrite length cap.
6. **Prompt drift**: keep prompts in resource files with golden tests so a casual edit does not silently change behavior.
7. **Ollama context window default is small**: set `num_ctx` explicitly in Spring AI Ollama options, or your long context will be silently truncated.