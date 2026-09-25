# Chunk Norris: Phases and Tasks

`MILESTONES.md` says **why**. This file says **what to do next**.

## How this file works

- **Phases** are ordered. Finish one before starting the next.
- **Tasks** are atomic: each one changes **exactly one file** (new or existing).
- Tasks are ordered so dependencies come first, so **the project compiles after every task**.
- **▶ Verify** steps touch no files. They are how you prove the phase works before moving on.
- Every phase ends with a **Checkpoint**: verify, then commit.
- If a task turns out to need a second file, split it into two tasks rather than bending the rule.

### Path shorthand

| Shorthand | Expands to |
|---|---|
| `{pkg}` | `src/main/java/github/mralmostcool/chunk_norris` |
| `{res}` | `src/main/resources` |
| `{test}` | `src/test/java/github/mralmostcool/chunk_norris` |

### Verify against your version

Spring AI moves fast and you are on Boot 4. Where a task names a Spring AI class or method, treat the name as a pointer, and confirm the exact signature in the reference docs for your version.

---

## Phase 0: Foundation ✅

- [x] 0.1 `pom.xml`: Spring Web, Ollama, pgvector, Postgres driver, Docker Compose support, springdoc
- [x] 0.2 `compose.yaml`: pgvector service with `./data/postgres` bind mount
- [x] 0.3 `application.yml`: Ollama, datasource, pgvector settings
- [x] 0.4 `{pkg}/HelloVectorController.java`: seed, search, clear endpoints

---

## Phase 1: Housekeeping

**Goal:** a clean base before real code arrives.

- [x] 1.1 `.gitignore`: add `data/`
  ↳ Done when: `git status` no longer lists anything under `data/`
- [x] 1.2 `{pkg}/dev/HelloVectorController.java`: move into a `dev` package (IDE refactor → move)
  ↳ Done when: it compiles with the updated `package` line
- [x] 1.3 ▶ Verify: start the app; `/api/hello/seed` and `/api/hello/search` still work

**Checkpoint:** commit `chore: move hello controller to dev package`

---

## Phase 2: Configuration

**Goal:** all tunable RAG settings live in one typed place, not scattered magic numbers.

- [x] 2.1 `{pkg}/config/RagProperties.java` *(new)*: record with `@ConfigurationProperties(prefix = "rag")`; fields `chunkSize`, `topK`, `similarityThreshold`, `uploadDir`
  ↳ Done when: compiles
- [x] 2.2 `{pkg}/ChunkNorrisApplication.java`: add `@ConfigurationPropertiesScan`
  ↳ Done when: compiles
- [x] 2.3 `{res}/application.yml`: add a `rag:` block with `chunk-size: 300`, `top-k: 4`, `similarity-threshold: 0.5`, `upload-dir: data/uploads` (nested under the existing single top-level structure; `rag` is its own top-level key, not under `spring`)
  ↳ Done when: app boots with no binding errors
- [x] 2.4 `{res}/application.yml`: add multipart limits (`spring.servlet.multipart.max-file-size: 20MB`, `max-request-size: 20MB`)
  ↳ Done when: app boots

**Checkpoint:** commit `feat: add rag configuration properties`

---

## Phase 3: File Storage

**Goal:** uploaded originals are saved safely under `data/uploads`.

- [x] 3.1 `{pkg}/ingestion/FileStorageService.java` *(new)*: constructor takes `RagProperties` and creates the upload directory if missing
  ↳ Done when: app boots and `data/uploads/` appears
- [x] 3.2 `{pkg}/ingestion/FileStorageService.java`: add `Path save(UUID documentId, MultipartFile file)`; clean the filename (`StringUtils.cleanPath`) and reject anything containing `..`
  ↳ Done when: compiles

**Checkpoint:** commit `feat: file storage service`

---

## Phase 4: Document Reading

**Goal:** turn a file into text `Document`s.

- [x] 4.1 `pom.xml`: add the Spring AI Tika document reader (version managed by the Spring AI BOM; do not pin manually)
  ↳ Done when: Maven reload succeeds and the app still boots
- [x] 4.2 `{pkg}/ingestion/DocumentReaderFactory.java` *(new)*: `List<Document> read(Resource resource)` using `TikaDocumentReader`
  ↳ Done when: compiles
- [x] 4.3 `src/test/resources/sample-docs/sample.md` *(new)*: about a page of text on a topic you know well
  ↳ Done when: file exists
- [x] 4.4 `{test}/ingestion/DocumentReaderFactoryTest.java` *(new)*: reads `sample.md`, asserts the text is non-empty
  ↳ Done when: test passes
- [x] 4.5 ▶ Verify: `./mvnw.cmd test` is green

**Checkpoint:** commit `feat: document reader`

---

## Phase 5: Chunking

**Goal:** split text into pieces small enough for the embedding model.

- [x] 5.1 `{pkg}/ingestion/ChunkingService.java` *(new)*: wraps `TokenTextSplitter` configured from `RagProperties.chunkSize`; method `List<Document> chunk(List<Document>)`
  ↳ Done when: compiles (check the splitter's constructor or builder in your version)
- [x] 5.2 `{test}/ingestion/ChunkingServiceTest.java` *(new)*: long input produces more than one chunk
  ↳ Done when: test passes
- [x] 5.3 `{test}/ingestion/ChunkingServiceTest.java`: add an assertion that no chunk exceeds a character ceiling (roughly `chunkSize * 6`)
  ↳ Done when: test passes
- [x] 5.4 ▶ Verify: print the chunks once and read them; do the boundaries make sense?

**Checkpoint:** commit `feat: chunking service`

---

## Phase 6: Hashing and Metadata

**Goal:** make ingestion repeatable (no duplicates) and citation-ready.

- [ ] 6.1 `{pkg}/ingestion/ContentHasher.java` *(new)*: `String sha256(byte[] bytes)`
  ↳ Done when: compiles
- [ ] 6.2 `{pkg}/ingestion/ContentHasher.java`: add `UUID chunkId(String fileHash, int chunkIndex)` using `UUID.nameUUIDFromBytes`
  ↳ Done when: compiles (pgvector's id column is a UUID in the default configuration)
- [ ] 6.3 `{test}/ingestion/ContentHasherTest.java` *(new)*: same input gives same hash and same chunk id; different input differs
  ↳ Done when: test passes
- [ ] 6.4 `{pkg}/ingestion/MetadataEnricher.java` *(new)*: takes chunks, `documentId`, `filename`, `fileHash`; returns new `Document`s with deterministic ids and metadata `documentId`, `filename`, `chunkIndex`, `fileHash`, `uploadedAt`
  ↳ Done when: compiles (confirm how to construct a `Document` with an explicit id in your version)
- [ ] 6.5 `{test}/ingestion/MetadataEnricherTest.java` *(new)*: metadata keys present, ids stable across two runs
  ↳ Done when: test passes

**Checkpoint:** commit `feat: content hashing and metadata enrichment`

---

## Phase 7: Document Registry

**Goal:** track which documents exist (the vector table only knows about chunks).

- [ ] 7.1 `{res}/schema.sql` *(new)*: `CREATE TABLE IF NOT EXISTS documents (id uuid primary key, filename text, file_hash text unique, status text, chunk_count int, stored_path text, uploaded_at timestamptz)`
  ↳ Done when: file exists
- [ ] 7.2 `{res}/application.yml`: set `spring.sql.init.mode: always`
  ↳ Done when: app boots
- [ ] 7.3 `{pkg}/ingestion/IngestionStatus.java` *(new)*: enum `PENDING, PROCESSING, DONE, FAILED`
  ↳ Done when: compiles
- [ ] 7.4 `{pkg}/ingestion/DocumentRecord.java` *(new)*: record mirroring the table columns
  ↳ Done when: compiles
- [ ] 7.5 `{pkg}/ingestion/DocumentRegistry.java` *(new)*: `JdbcTemplate` constructor plus `insert(DocumentRecord)`
  ↳ Done when: compiles
- [ ] 7.6 `{pkg}/ingestion/DocumentRegistry.java`: add `Optional<DocumentRecord> findByHash(String)`
  ↳ Done when: compiles
- [ ] 7.7 `{pkg}/ingestion/DocumentRegistry.java`: add `Optional<DocumentRecord> findById(UUID)` and `List<DocumentRecord> findAll()`
  ↳ Done when: compiles
- [ ] 7.8 `{pkg}/ingestion/DocumentRegistry.java`: add `updateStatus(UUID, IngestionStatus, int chunkCount)`
  ↳ Done when: compiles
- [ ] 7.9 `{pkg}/ingestion/DocumentRegistry.java`: add `delete(UUID)`
  ↳ Done when: compiles
- [ ] 7.10 ▶ Verify the table exists (adjust the service name to match your `compose.yaml`):
  `docker compose exec pgvector psql -U chunknorris -d chunknorris -c "\d documents"`

**Checkpoint:** commit `feat: document registry`

---

## Phase 8: Ingestion Pipeline

**Goal:** `POST /api/documents` takes a file and produces searchable chunks.

- [ ] 8.1 `{pkg}/ingestion/dto/UploadResponse.java` *(new)*: record `documentId`, `filename`, `status`, `chunkCount`, `duplicate`
  ↳ Done when: compiles
- [ ] 8.2 `{pkg}/ingestion/IngestionService.java` *(new)*: constructor injecting collaborators; `UploadResponse ingest(MultipartFile)` hashes the file and, if the hash already exists in the registry, returns the existing record with `duplicate = true`
  ↳ Done when: compiles
- [ ] 8.3 `{pkg}/ingestion/IngestionService.java`: after the duplicate check, save the file and insert a registry row with status `PROCESSING`
  ↳ Done when: compiles
- [ ] 8.4 `{pkg}/ingestion/IngestionService.java`: read the saved file, chunk it, enrich with metadata
  ↳ Done when: compiles
- [ ] 8.5 `{pkg}/ingestion/IngestionService.java`: `vectorStore.add(chunks)`, then mark `DONE` with the chunk count; wrap in try/catch that marks `FAILED` and rethrows
  ↳ Done when: compiles
- [ ] 8.6 `{pkg}/ingestion/DocumentController.java` *(new)*: `POST /api/documents`, multipart param `file`, returns `UploadResponse`
  ↳ Done when: app boots
- [ ] 8.7 ▶ Verify upload (use `curl.exe`, not the PowerShell `curl` alias):
  `curl.exe -F "file=@C:\path\to\sample.pdf" http://localhost:8080/api/documents`
  Then check chunks:
  `docker compose exec pgvector psql -U chunknorris -d chunknorris -c "select metadata->>'filename' as file, count(*) from vector_store group by 1;"`
- [ ] 8.8 ▶ Verify deduplication: upload the same file again; response has `duplicate: true` and the chunk count is unchanged

**Checkpoint:** commit `feat: document ingestion pipeline`. **This completes Milestone 2's core.**

---

## Phase 9: List and Delete

**Goal:** manage what has been ingested.

- [ ] 9.1 `{pkg}/ingestion/dto/DocumentResponse.java` *(new)*: record for API output
  ↳ Done when: compiles
- [ ] 9.2 `{pkg}/ingestion/DocumentController.java`: add `GET /api/documents`
  ↳ Done when: returns the uploaded documents
- [ ] 9.3 `{pkg}/ingestion/FileStorageService.java`: add `void delete(Path)`
  ↳ Done when: compiles
- [ ] 9.4 `{pkg}/ingestion/IngestionService.java`: add `delete(UUID)` which removes the document's chunks by metadata filter (`documentId == '...'`), deletes the stored file, and deletes the registry row
  ↳ Done when: compiles (confirm the filter-based `VectorStore.delete` in your version; this may also replace the raw SQL in your dev clear endpoint)
- [ ] 9.5 `{pkg}/ingestion/DocumentController.java`: add `DELETE /api/documents/{id}`
  ↳ Done when: app boots
- [ ] 9.6 ▶ Verify: upload two files, delete one; only that file's chunks disappear

**Checkpoint:** commit `feat: list and delete documents`

---

## Phase 10: Retrieval

**Goal:** a search layer separate from the LLM, so you can judge retrieval on its own.

- [ ] 10.1 `{pkg}/retrieval/RetrievedChunk.java` *(new)*: record `text`, `metadata`, `score`
  ↳ Done when: compiles
- [ ] 10.2 `{pkg}/retrieval/RetrievalService.java` *(new)*: `List<RetrievedChunk> retrieve(String query)` using `topK` and `similarityThreshold` from `RagProperties`
  ↳ Done when: compiles (check how to read the score from a `Document` in your version)
- [ ] 10.3 `{pkg}/retrieval/SearchController.java` *(new)*: `GET /api/search?q=...` returns chunks with scores (debug endpoint, no LLM)
  ↳ Done when: app boots
- [ ] 10.4 ▶ Verify: ask 5 questions you know the answers to; note the scores of good vs irrelevant chunks
- [ ] 10.5 `{res}/application.yml`: set `rag.similarity-threshold` based on what you observed
  ↳ Done when: irrelevant queries return few or no chunks

**Checkpoint:** commit `feat: retrieval service`

---

## Phase 11: RAG Endpoint

**Goal:** ask a question, get a grounded answer. The prompt is hand-built first so you see exactly what the model receives.

- [ ] 11.1 `{res}/prompts/rag-system.st` *(new)*: system prompt: answer only from the provided context; if the answer is not there, say you don't know
  ↳ Done when: file exists
- [ ] 11.2 `{res}/prompts/rag-user.st` *(new)*: user template with `{context}` and `{question}` placeholders
  ↳ Done when: file exists
- [ ] 11.3 `{pkg}/chat/PromptTemplates.java` *(new)*: loads both files from the classpath; `String system()` and `String user(String context, String question)` (use Spring AI's `PromptTemplate`, or plain `String.replace` if delimiters give you trouble)
  ↳ Done when: compiles
- [ ] 11.4 `{pkg}/config/ChatClientConfig.java` *(new)*: `ChatClient` bean built from `ChatClient.Builder`
  ↳ Done when: app boots
- [ ] 11.5 `{pkg}/chat/dto/AskRequest.java` *(new)*: record with `question`
  ↳ Done when: compiles
- [ ] 11.6 `{pkg}/chat/dto/AskResponse.java` *(new)*: record with `answer`
  ↳ Done when: compiles
- [ ] 11.7 `{pkg}/chat/RagService.java` *(new)*: `AskResponse ask(AskRequest)`; retrieve chunks and, if none come back, return a fixed "I don't know" **without calling the LLM**
  ↳ Done when: compiles
- [ ] 11.8 `{pkg}/chat/RagService.java`: join chunk texts into a context string, build the prompts, call the `ChatClient`, return the answer
  ↳ Done when: compiles
- [ ] 11.9 `{pkg}/chat/RagService.java`: log the final prompt at DEBUG level
  ↳ Done when: prompt visible in logs when debug logging is on
- [ ] 11.10 `{pkg}/chat/AskController.java` *(new)*: `POST /api/ask`
  ↳ Done when: app boots
- [ ] 11.11 ▶ Verify:
  `Invoke-RestMethod -Method Post http://localhost:8080/api/ask -ContentType "application/json" -Body '{"question":"your question here"}'`
  Try one question covered by your docs and one that is not. Then run `ollama ps` during a request: still `100% GPU`?
- [ ] 11.12 `{res}/application.yml`: if VRAM spilled to CPU, lower `spring.ai.ollama.chat.options.num-ctx` or `rag.top-k`
  ↳ Done when: `ollama ps` shows `100% GPU` during a request *(skip if it already did)*

**Checkpoint:** commit `feat: rag endpoint`. **Milestone 3 complete.**

---

## Phase 12: Citations

**Goal:** every answer shows where it came from.

- [ ] 12.1 `{pkg}/chat/dto/SourceReference.java` *(new)*: record `documentId`, `filename`, `chunkIndex`, `snippet`, `score`
  ↳ Done when: compiles
- [ ] 12.2 `{pkg}/chat/dto/AskResponse.java`: add `List<SourceReference> sources`
  ↳ Done when: compiles (fix callers as the compiler directs)
- [ ] 12.3 `{pkg}/chat/RagService.java`: map retrieved chunks to `SourceReference` (snippet trimmed to about 200 characters)
  ↳ Done when: `/api/ask` returns a `sources` array
- [ ] 12.4 `{pkg}/chat/RagService.java`: number the context chunks (`[1] ...`, `[2] ...`)
  ↳ Done when: the logged prompt shows numbered chunks
- [ ] 12.5 `{res}/prompts/rag-system.st`: instruct the model to cite chunk numbers like `[1]`
  ↳ Done when: answers include bracketed citations
- [ ] 12.6 ▶ Verify: cited numbers match the `sources` list

**Checkpoint:** commit `feat: citations`

---

## Phase 13: Filtering

**Goal:** restrict a question to specific documents.

- [ ] 13.1 `{pkg}/chat/dto/AskRequest.java`: add optional `List<UUID> documentIds`
  ↳ Done when: compiles
- [ ] 13.2 `{pkg}/retrieval/SearchFilterBuilder.java` *(new)*: builds a filter expression for `documentId in [...]` (look at `FilterExpressionBuilder` in your version)
  ↳ Done when: compiles
- [ ] 13.3 `{pkg}/retrieval/RetrievalService.java`: add `retrieve(String query, List<UUID> documentIds)` applying the filter when the list is non-empty
  ↳ Done when: compiles
- [ ] 13.4 `{pkg}/chat/RagService.java`: pass `documentIds` from the request into retrieval
  ↳ Done when: compiles
- [ ] 13.5 ▶ Verify: with two documents ingested, a filtered question returns sources only from the chosen document

**Checkpoint:** commit `feat: document filtering`. **Milestone 4 complete.**

---

## Phase 14: Evaluation

**Goal:** measure instead of guessing.

- [ ] 14.1 `eval/questions.json` *(new)*: 10-15 questions, each with `question`, `expectedFilename`, `answerable` (include 3-5 unanswerable ones)
  ↳ Done when: valid JSON
- [ ] 14.2 `{pkg}/evaluation/EvalQuestion.java` *(new)*: record matching the JSON
  ↳ Done when: compiles
- [ ] 14.3 `{pkg}/evaluation/EvalResult.java` *(new)*: record `question`, `hit`, `refused`, `latencyMs`, `answer`
  ↳ Done when: compiles
- [ ] 14.4 `{pkg}/evaluation/EvaluationRunner.java` *(new)*: load the JSON; for each question, retrieve and record whether the expected file appears in the top-k
  ↳ Done when: compiles
- [ ] 14.5 `{pkg}/evaluation/EvaluationRunner.java`: for unanswerable questions, call `RagService` and record whether the model refused
  ↳ Done when: compiles
- [ ] 14.6 `{pkg}/evaluation/EvaluationRunner.java`: record latency per question and compute hit rate and refusal rate
  ↳ Done when: compiles
- [ ] 14.7 `{pkg}/evaluation/EvaluationController.java` *(new)*: `GET /api/eval/run` returns the report
  ↳ Done when: returns a report
- [ ] 14.8 `docs/experiments.md` *(new)*: results table template (setting, value, hit rate, refusal rate, avg latency, notes)
  ↳ Done when: file exists

### Experiments

Each experiment is **two tasks in two files**: change one setting, then record the result. Re-ingest only where noted.

| # | Change (in `application.yml`) | Values to try | Re-ingest? |
|---|---|---|---|
| E1 | `rag.chunk-size` | 200, 300, 400 | Yes |
| E2 | `rag.top-k` | 3, 5, 8 | No |
| E3 | `rag.similarity-threshold` | 0.4, 0.5, 0.6, 0.7 | No |
| E4 | chat model | `qwen2.5:7b` vs `llama3.2:3b` | No |
| E5 | embedding model | v2-moe vs `nomic-embed-text` | **Yes, and wipe `data/postgres` first** |

For every run: change the setting (`application.yml`), restart, run the eval, then record the numbers (`docs/experiments.md`).

**Checkpoint:** commit `feat: evaluation harness` and one commit per experiment. **Milestone 5 complete.**

---

## Phase 15: Hardening

**Goal:** behave like a real service.

**Errors**
- [ ] 15.1 `{pkg}/common/error/UnsupportedFileTypeException.java` *(new)*
- [ ] 15.2 `{pkg}/common/error/DocumentNotFoundException.java` *(new)*
- [ ] 15.3 `{pkg}/common/error/GlobalExceptionHandler.java` *(new)*: `@RestControllerAdvice` returning `ProblemDetail`
- [ ] 15.4 `{pkg}/ingestion/IngestionService.java`: reject empty files and unsupported extensions
- [ ] 15.5 `{pkg}/ingestion/IngestionService.java`: throw `DocumentNotFoundException` when deleting an unknown id
  ↳ Done when (15.1-15.5): bad uploads and unknown ids return clean JSON errors, not stack traces

**Health**
- [ ] 15.6 `pom.xml`: add `spring-boot-starter-actuator`
- [ ] 15.7 `{pkg}/common/health/OllamaHealthIndicator.java` *(new)*: pings Ollama (note: `HealthIndicator` moved packages in Boot 4; let the IDE find it)
  ↳ Done when: `/actuator/health` shows Ollama status

**Tests**
- [ ] 15.8 `pom.xml`: add Testcontainers PostgreSQL and JUnit support (Boot 4 compatible versions)
- [ ] 15.9 `{test}/support/PgVectorContainerConfig.java` *(new)*: pgvector container with `@ServiceConnection`
- [ ] 15.10 `{test}/ingestion/IngestionIntegrationTest.java` *(new)*: upload creates chunks; second upload creates none
- [ ] 15.11 `{test}/retrieval/RetrievalServiceTest.java` *(new)*: a document filter isolates results

**Async ingestion**
- [ ] 15.12 `{pkg}/ChunkNorrisApplication.java`: add `@EnableAsync`
- [ ] 15.13 `{pkg}/ingestion/IngestionService.java`: run chunk-and-embed asynchronously after the registry row is saved
- [ ] 15.14 `{pkg}/ingestion/DocumentController.java`: return `202 Accepted` and add `GET /api/documents/{id}` for status polling
  ↳ Done when: a large upload returns instantly and status moves `PROCESSING` → `DONE`

**Docs**
- [ ] 15.15 `README.md` *(new)*: prerequisites, how to run, endpoints, architecture diagram

**Checkpoint:** commit per group. **Milestone 6 complete.**

---

## Phase 16: Stretch (break into atomic tasks when you get here)

- [ ] Custom overlapping splitter (see note below)
- [ ] Compare hand-rolled prompt vs `QuestionAnswerAdvisor`
- [ ] Streaming answers (SSE)
- [ ] Multi-turn conversation memory
- [ ] Query rewriting for vague follow-ups
- [ ] Hybrid search (vector plus Postgres full-text)
- [ ] Re-ranking retrieved chunks
- [ ] Structured JSON output mapped to records
- [ ] Simple web UI
- [ ] Spring Modulith boundary verification (check Boot 4 support first)

---

## Changes from MILESTONES.md

1. **Overlap:** `MILESTONES.md` lists chunk overlap as a `TokenTextSplitter` setting. As far as I know that splitter doesn't expose one, so verify in your version. If it doesn't, overlap becomes a custom-splitter stretch task, and the overlap experiment moves there.
2. **Token counts are approximate:** the splitter counts tokens with a GPT-style tokenizer, not the embedding model's own. That is why the starting chunk size is 300, not the 400 mentioned earlier, leaving headroom under the 512-token limit.
3. **Hand-rolled prompt first:** so you can see exactly what the LLM receives. The advisor comparison is a stretch task.
4. **Registry:** plain `JdbcTemplate` plus `schema.sql`, no JPA.