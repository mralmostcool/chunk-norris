# Chunk Norris: Project Milestones

> A zero-cost learning project exploring Retrieval-Augmented Generation (RAG) in Spring Boot with Spring AI.
> Everything runs locally: Ollama for chat and embeddings, pgvector in Docker for storage.

**Two files, two jobs:**
- **This file (`MILESTONES.md`)** explains *why*: goals, concepts to understand, acceptance tests.
- **`TASKS.md`** explains *what to do next*: ordered phases broken into atomic, one-file tasks.

Work through `TASKS.md`; come back here to check you actually understood the milestone before ticking it off.

## Milestone to Phase Map

| Milestone | Topic | Phases in `TASKS.md` |
|---|---|---|
| 0 | Foundation | Phase 0 |
| 1 | Hello Vector | Phase 0 (task 0.4), Phase 1 |
| 2 | Ingestion pipeline | Phases 2-9 |
| 3 | RAG endpoint | Phases 10-11 |
| 4 | Citations and filtering | Phases 12-13 |
| 5 | Quality and evaluation | Phase 14 |
| 6 | Engineering hardening | Phase 15 |
| 7 | Stretch goals | Phase 16 |

---

## Stack Snapshot

| Layer | Choice | Notes |
|---|---|---|
| Framework | Spring Boot 4.1.1, Java 21 | Boot 4: check every new dependency for Boot 4 support |
| AI abstraction | Spring AI | Verify API and property names against the docs for your version |
| Chat model | `qwen2.5:7b` (Ollama, native on Windows) | ~4.7 GB; fits the RTX 2070 (8 GB VRAM) |
| Embedding model | `nomic-embed-text-v2-moe` | 768 dims, **512 token max**; start with `chunk-size: 300` (see Milestone 2 note on token counting) |
| Vector store | pgvector (`pgvector/pgvector:pg16`) | Data bind-mounted at `./data/postgres` |
| Document registry | Plain `JdbcTemplate` + `schema.sql` | No JPA; tracks which documents exist |
| Infra | Docker Desktop + `compose.yaml` | Boot's Docker Compose support starts it with the app |
| Package | `github.mralmostcool.chunk_norris` | |

## Ground Rules

- **Zero cost.** No paid APIs, no cloud services, no hosted vector DBs.
- **One embedding model per table.** If you change the model or dimensions, wipe `data/postgres` and re-ingest.
- **One compose file.** `compose.yaml` only; no `docker-compose.yml`.
- **One `spring:` key** in `application.yml`.
- **Check Boot 4 compatibility** before adding any third-party library (springdoc needs a 3.x version, for example).
- **Native Ollama only.** Do not add an `ollama` service to the compose file.
- **One file per task.** If a task needs two files, split it (see `TASKS.md`).

---

## Milestone 0: Foundation ✅

**Goal:** a running Spring Boot app connected to Ollama and pgvector.

- [x] Repo initialised, project generated from Spring Initializr
- [x] Ollama installed; chat and embedding models pulled
- [x] `compose.yaml` with pgvector service and `./data/postgres` bind mount
- [x] `application.yml` configured (Ollama, datasource, pgvector: 768 dims, cosine, HNSW)
- [x] App boots; `vector_store` table initialised
- [x] Swagger UI working (springdoc version compatible with Boot 4)

**Lessons learned (keep these):**
- Initializr's generated `compose.yaml` may include an `ollama` container; remove it
- Explicit `spring.datasource.*` avoids reliance on Docker Compose auto-detection
- Docker Desktop's engine must be running before `spring-boot:run`
- Springdoc 2.x targets Boot 3; Boot 4 needs a 3.x version

---

## Milestone 1: Hello Vector ✅

**Goal:** prove the embed, store, similarity search loop end to end.

- [x] `POST /api/hello/seed` adds sample documents
- [x] `GET /api/hello/search?q=...` returns top-k matches
- [x] `DELETE /api/hello/clear` wipes the store

**Acceptance test:** query `which dog likes games` returns the golden retriever sentence first, with no shared keywords.

**Concepts to own:**
- What an embedding is (text becomes 768 floats) and why nearby vectors mean similar meaning
- Cosine distance vs similarity
- What `topK` does
- Why repeated seeding creates duplicates (random IDs)
- What `JdbcTemplate` is and why the clear endpoint uses raw SQL

---

## Milestone 2: Ingestion Pipeline

**Goal:** upload a real document and turn it into searchable chunks.

### Deliverables
- [ ] Typed RAG settings in one place (`RagProperties`: chunk size, top-k, threshold, upload dir)
- [ ] `POST /api/documents` accepting multipart file upload (PDF, Markdown, TXT to start)
- [ ] Original files saved under `./data/uploads/` (gitignored)
- [ ] Parse with the Spring AI Tika document reader; take the version from the Spring AI BOM
- [ ] Split with `TokenTextSplitter`, **starting at 300 tokens** (see note below)
- [ ] Attach metadata to every chunk: `documentId`, `filename`, `chunkIndex`, `fileHash`, `uploadedAt`
- [ ] Deterministic chunk IDs derived from the file hash, so re-uploads cannot create duplicates
- [ ] A `documents` registry table (via `schema.sql` and `JdbcTemplate`) tracking status and chunk count
- [ ] `GET /api/documents` lists ingested documents
- [ ] `DELETE /api/documents/{id}` removes a document, its stored file, and all its chunks
- [ ] Move the hardcoded `/api/hello` seed into a `dev` package; real ingestion replaces it

### Acceptance tests
- Upload a 10+ page PDF; chunks appear in `vector_store` with correct metadata
- Uploading the **same file twice** does not create duplicates (response says `duplicate: true`)
- Deleting a document removes all of its chunks and only its chunks
- Similarity search over your document returns relevant chunks for a question you know the answer to

### Concepts to own
- Why chunking exists (embedding limits, retrieval precision)
- Chunk size trade-offs (small = precise but context-poor; large = context-rich but noisy and risks truncation)
- Silent truncation when a chunk exceeds the embedding model's max tokens
- Metadata as the foundation for citations and filtering
- Idempotent ingestion (same input, same result)

### Note: token counting is approximate
`TokenTextSplitter` counts tokens with a GPT-style tokenizer, not the embedding model's own. The two disagree by some margin, so a "400 token" chunk may exceed 400 tokens in `nomic-embed-text-v2-moe`'s eyes. Starting at 300 leaves headroom under the 512 limit. Chunk size is tested properly in Milestone 5.

### Note: overlap
Chunk overlap is a common RAG technique, but I don't believe `TokenTextSplitter` exposes an overlap setting (verify in your version). If it doesn't, overlap is a custom-splitter task in Milestone 7, not a config change.

### Watch out for
- Scanned PDFs have no text layer (Tika returns nothing; OCR is out of scope for now)
- Large uploads: check Spring's multipart size limits
- First embedding call after idle is slow (Ollama reloading the model)
- `spring.sql.init.mode` must be `always` for `schema.sql` to run against Postgres

---

## Milestone 3: The RAG Endpoint

**Goal:** ask a question, get an answer grounded in your documents.

### Deliverables
- [ ] A standalone retrieval layer with a debug search endpoint (no LLM), so you can judge retrieval separately from generation
- [ ] `POST /api/ask` with `{ "question": "..." }`
- [ ] A `ChatClient` bean configured for Ollama
- [ ] **Hand-built prompt first**: retrieve chunks, join them into a context string, fill a prompt template, call the model. This shows you exactly what the LLM receives. (`QuestionAnswerAdvisor` comparison is a Milestone 7 task.)
- [ ] Prompt templates stored as files (`prompts/rag-system.st`, `prompts/rag-user.st`)
- [ ] A system prompt instructing the model to answer only from the provided context and to say it doesn't know otherwise
- [ ] If retrieval returns nothing above the threshold, answer "I don't know" **without calling the LLM**
- [ ] Tune `topK` and a similarity threshold using observed scores
- [ ] Set `num-ctx` (start at 8192) and low temperature (0.1-0.3)
- [ ] Log the final prompt at DEBUG level

### Acceptance tests
- A question answered in your docs returns a correct, grounded answer
- A question **not** covered by your docs gets an "I don't know" style response, not an invented answer
- `ollama ps` shows the chat model still at `100% GPU` during a RAG request

### Concepts to own
- The RAG loop: embed query, retrieve, augment prompt, generate
- Prompt stuffing and context window limits
- Why low temperature helps grounded answers
- The difference between a retrieval failure and a generation failure
- Why a similarity threshold matters (it decides when to refuse)

### Watch out for
- Default Ollama context is small; retrieved chunks can be silently cut off
- VRAM spill to CPU when context grows (drop `num-ctx`, `topK`, or chunk size)

---

## Milestone 4: Citations and Filtering

**Goal:** make answers verifiable and scoped.

### Deliverables
- [ ] Response DTO: `{ answer, sources: [{ documentId, filename, chunkIndex, snippet, score }] }`
- [ ] Number the context chunks (`[1]`, `[2]`, ...) and instruct the model to cite them
- [ ] Optional request filter: restrict a question to specific `documentId`s
- [ ] Implement filtering with Spring AI's metadata filter expressions
- [ ] (Optional) tags/categories at upload time and filtering by tag

### Acceptance tests
- Every answer lists the sources it drew from
- Cited numbers in the answer match the `sources` list
- Asking with a `documentId` filter never returns chunks from other documents
- Scores let you distinguish a strong match from a weak one

### Concepts to own
- Metadata filtering vs pure semantic search
- Why citations are the main defence against hallucination
- Score interpretation (distance vs similarity)

---

## Milestone 5: Quality and Evaluation

**Goal:** stop guessing; measure what changes retrieval quality.

### Deliverables
- [ ] An evaluation set in the repo (`eval/questions.json`): 10-15 questions with the expected source file, including 3-5 that **should not** be answerable
- [ ] A runner that records per question: whether the expected source was in the top-k, whether the model refused when it should, and latency
- [ ] An endpoint that returns the report (hit rate, refusal rate, average latency)
- [ ] Experiments, each written up in `docs/experiments.md` (change one setting, re-run, record):
  - [ ] **E1** chunk size: 200 vs 300 vs 400 (re-ingest each time)
  - [ ] **E2** `topK`: 3 vs 5 vs 8
  - [ ] **E3** similarity threshold: 0.4 to 0.7
  - [ ] **E4** chat model: `qwen2.5:7b` vs `llama3.2:3b`
  - [ ] **E5** embedding model: v2-moe vs `nomic-embed-text` (wipe `data/postgres` and re-ingest)
- [ ] A short conclusion: which settings you chose and why

*(The overlap experiment from the earlier draft moved to Milestone 7, since it depends on a custom splitter.)*

### Acceptance tests
- You can point to numbers, not vibes, for every configuration choice
- You can explain a specific case where retrieval failed and what fixed it

### Concepts to own
- Retrieval metrics: hit rate / recall@k
- Groundedness and refusal behaviour
- Diagnosing retrieval failure vs generation failure

---

## Milestone 6: Engineering Hardening

**Goal:** treat it like a real service.

- [ ] Clean error handling: custom exceptions, a `@RestControllerAdvice`, `ProblemDetail` responses
- [ ] Validation: reject empty files and unsupported types
- [ ] Health check reporting Ollama connectivity (Actuator; note the `HealthIndicator` package changed in Boot 4)
- [ ] Integration tests using **Testcontainers** with the pgvector image (Boot 4 compatible versions)
- [ ] Unit tests for chunking, hashing, and metadata (most already written in Milestone 2)
- [ ] Async ingestion for large files with a status endpoint (`PENDING`, `PROCESSING`, `DONE`, `FAILED`)
- [ ] README with setup steps and architecture diagram

---

## Milestone 7: Stretch Goals (pick what interests you)

- [ ] **Custom overlapping splitter** (if `TokenTextSplitter` lacks overlap), then rerun the chunking experiment with 0 / 10% / 20% overlap
- [ ] **Advisor comparison**: rebuild the RAG flow with `QuestionAnswerAdvisor` and compare its behaviour to your hand-built version
- [ ] **Streaming** answers (`Flux<String>` / SSE) for token-by-token output
- [ ] **Conversation memory**: multi-turn chat that remembers earlier questions
- [ ] **Query rewriting**: use the LLM to rewrite vague follow-ups before retrieval
- [ ] **Hybrid search**: combine pgvector similarity with Postgres full-text search
- [ ] **Re-ranking** retrieved chunks before prompting
- [ ] **Structured output**: have the model return JSON answers mapped to Java records
- [ ] **Tool/function calling** with a local model that supports it
- [ ] **Simple UI**: a single-page chat front end with source display
- [ ] **Index tuning**: compare HNSW vs IVFFlat, explore Matryoshka truncation to 256 dims (requires full re-ingestion)
- [ ] **Spring Modulith** boundary verification (check Boot 4 support first)

---

## Decisions Log

Record choices and the reasons behind them as you go.

| Date | Decision | Reason |
|---|---|---|
| 2026-09-24 | Local Ollama + pgvector, zero cost | Learning focus, no billing risk |
| 2026-09-24 | `qwen2.5:7b` chat model | Fits 8 GB VRAM, strong instruction following |
| 2026-09-24 | `nomic-embed-text-v2-moe` (768 dims) | Chosen embedding model; 512 token limit shapes chunking |
| 2026-09-24 | Bind mount `./data/postgres` | Keep data inside the project; gitignored |
| 2026-09-24 | Starting chunk size 300 | Token counts are approximate; leave headroom under 512 |
| 2026-09-24 | Hand-built RAG prompt before using advisors | See exactly what the model receives |
| 2026-09-24 | Registry via `JdbcTemplate` + `schema.sql` | Simplest option, no JPA dependency |
| 2026-09-24 | Deterministic chunk IDs from file hash | Makes re-ingestion idempotent |

## Open Questions

- Does `TokenTextSplitter` in your Spring AI version support overlap? (If not, see Milestone 7.)
- Does `VectorStore` support delete-by-filter in your version? (Needed for deleting one document's chunks; may also replace the raw SQL in the dev clear endpoint.)
- Best chunk size for your actual documents (answered in Milestone 5)
- Is the MoE embedding model measurably better than `nomic-embed-text` for your data?

## Housekeeping Checklist

- [ ] `.gitignore` includes `target/`, `data/`, IDE files
- [ ] No secrets in the repo (none should be needed)
- [ ] Commit at the end of each phase with a clear message
- [ ] Update the Decisions Log when you change anything structural