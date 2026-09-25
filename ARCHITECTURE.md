# Chunk Norris Architecture & Workflow Specification

## Overview

Chunk Norris is a zero-cost local Retrieval-Augmented Generation (RAG) system built with **Spring Boot 4.1.1 (Java 21)**, **Spring AI**, **Ollama** (`qwen2.5:7b` chat model + `nomic-embed-text-v2-moe` embedding model), and **pgvector** (`pgvector/pgvector:pg16` in Docker).

## 1. System Architecture Overview

### 1.1 Generalized Tiered Architecture (All Key Modules)

```mermaid
graph TB
    subgraph Tier1 ["Tier 1: Client & REST API Gateway"]
        direction LR
        EP_DOC["/api/documents<br/>(Upload, List, Delete)"]
        EP_SRCH["/api/search<br/>(Debug Retrieval)"]
        EP_ASK["/api/ask<br/>(RAG Chat)"]
        EP_EVAL["/api/eval/run<br/>(Quality Harness)"]
        EP_ACT["/actuator/health<br/>(Ollama & DB Health)"]
    end

    subgraph Tier2 ["Tier 2: Core Domain & Orchestration Services"]
        INGEST_SRV["Ingestion Service<br/>(Duplicate Guard, Async Workflow, Registry Updates)"]
        RETR_SRV["Retrieval Service<br/>(Score Thresholding, Metadata Filter Assembly)"]
        RAG_SRV["Rag Service<br/>(Context Injection, Refusal Guard, Source Citing)"]
        EVAL_SRV["Evaluation Runner<br/>(Hit Rate, Refusal Rate & Latency Stats)"]
    end

    subgraph Tier3 ["Tier 3: Processing, Parsing & Template Engines"]
        PARSER["Tika Document Reader<br/>(PDF, TXT, Markdown Parser)"]
        SPLITTER["Token Text Splitter<br/>(300-Token Headroom Window)"]
        HASHER["Content Hasher & UUID Gen<br/>(SHA-256 Checksum, UUIDv3 Chunk IDs)"]
        ENRICHER["Metadata Enricher<br/>(Doc ID, Chunk Index, Hash, Timestamp)"]
        PROMPTS["Prompt Template Engine<br/>(rag-system.st & rag-user.st Rendering)"]
    end

    subgraph Tier4 ["Tier 4: Spring AI & Spring Data Framework Abstractions"]
        VS_ABS["Spring AI VectorStore Interface"]
        CC_ABS["Spring AI ChatClient Interface"]
        EM_ABS["Spring AI EmbeddingModel Interface"]
        JDBC_ABS["Spring JdbcTemplate"]
    end

    subgraph Tier5 ["Tier 5: Infrastructure, Models & Physical Storage"]
        direction LR
        PG_DB[("PostgreSQL 16 + pgvector<br/>• DB: chunknorris<br/>• Tables: documents, vector_store")]
        OLLAMA_CHAT["Ollama Chat Runtime<br/>• qwen2.5:7b (GPU)"]
        OLLAMA_EMBED["Ollama Embed Runtime<br/>• nomic-embed-text-v2-moe"]
        DISK_STORAGE[("Local Disk Storage<br/>• ./data/uploads/<br/>• ./data/postgres/")]
    end

    %% API to Service Connections
    EP_DOC --> INGEST_SRV
    EP_SRCH --> RETR_SRV
    EP_ASK --> RAG_SRV
    EP_EVAL --> EVAL_SRV

    %% Ingestion Pipeline Connections
    INGEST_SRV --> HASHER
    INGEST_SRV --> DISK_STORAGE
    INGEST_SRV --> PARSER
    PARSER --> SPLITTER
    SPLITTER --> ENRICHER
    ENRICHER --> VS_ABS
    INGEST_SRV --> JDBC_ABS

    %% Retrieval & RAG Connections
    RAG_SRV --> RETR_SRV
    RETR_SRV --> VS_ABS
    RAG_SRV --> PROMPTS
    PROMPTS --> CC_ABS

    %% Framework to Infrastructure Connections
    VS_ABS --> EM_ABS
    EM_ABS --> OLLAMA_EMBED
    VS_ABS --> PG_DB
    CC_ABS --> OLLAMA_CHAT
    JDBC_ABS --> PG_DB
```

### 1.2 End-to-End Sequence Diagram

```mermaid
sequenceDiagram
    autonumber
    actor User
    participant Controller as REST Controllers
    participant Ingestion as Ingestion Service
    participant Storage as File Storage & Registry
    participant Reader as Tika Reader & Splitter
    participant VectorStore as pgvector Store
    participant Retrieval as Retrieval Service
    participant LLM as Ollama ChatClient

    %% Ingestion Flow
    rect rgb(230, 240, 255)
    note right of User: Ingestion Phase (POST /api/documents)
    User->>Controller: Upload File (MultipartFile)
    Controller->>Ingestion: ingest(file)
    Ingestion->>Storage: Compute SHA-256 hash & check duplicate
    alt Duplicate Hash Found
        Storage-->>Ingestion: Existing Document Record
        Ingestion-->>Controller: Return UploadResponse (duplicate=true)
        Controller-->>User: 200 OK (Duplicate skipped)
    else New File
        Ingestion->>Storage: Save file to ./data/uploads/ & create registry record (PROCESSING)
        Ingestion->>Reader: Read Resource via TikaDocumentReader
        Reader-->>Ingestion: List<Document> raw text
        Ingestion->>Reader: TokenTextSplitter (chunk-size=300)
        Reader-->>Ingestion: List<Document> text chunks
        Ingestion->>Ingestion: Enrich metadata (docId, filename, hash, deterministic chunk UUIDs)
        Ingestion->>VectorStore: vectorStore.add(chunks) -> Ollama Embeddings -> pgvector
        Ingestion->>Storage: updateStatus(DONE, chunkCount)
        Ingestion-->>Controller: UploadResponse (duplicate=false, chunkCount)
        Controller-->>User: 200 OK / 202 Accepted
    end
    end

    %% RAG / Chat Flow
    rect rgb(240, 255, 230)
    note right of User: Query & RAG Phase (POST /api/ask)
    User->>Controller: AskRequest (question, optional documentIds)
    Controller->>Retrieval: retrieve(question, documentIds)
    Retrieval->>VectorStore: Similarity Search (topK, similarityThreshold, metadata filter)
    VectorStore-->>Retrieval: List<RetrievedChunk> with similarity scores
    alt No chunks pass similarity threshold
        Retrieval-->>Controller: Empty retrieval list
        Controller-->>User: AskResponse ("I don't know", empty sources) [LLM NOT CALLED]
    else Relevant chunks found
        Retrieval-->>Controller: List<RetrievedChunk>
        Controller->>LLM: Assemble system prompt + user context template & call ChatClient
        LLM-->>Controller: Grounded answer string with citations [1], [2]
        Controller-->>User: AskResponse (answer, List<SourceReference>)
    end
    end
```

### 1.2 Module Responsibilities (Box Component Diagram)

```mermaid
graph TD
    subgraph ClientLayer ["Client & Ingestion Trigger Layer"]
        API["REST Controllers<br/>(Document, Search, Ask, Eval)"]
    end

    subgraph IngestionModule ["Ingestion Module (Storage, Parsing & Vectorizing)"]
        FS["FileStorageService<br/>- Save file to ./data/uploads/<br/>- File path cleaning & safety"]
        REG["DocumentRegistry<br/>- Track doc status (PENDING -> DONE)<br/>- DB table 'documents'"]
        HASH["ContentHasher<br/>- SHA-256 computation<br/>- Deterministic Chunk UUIDs"]
        READER["DocumentReaderFactory<br/>- Tika document extraction"]
        CHUNK["ChunkingService<br/>- TokenTextSplitter (chunkSize)"]
        ENRICH["MetadataEnricher<br/>- Add metadata (docId, hash, index)"]
    end

    subgraph RetrievalModule ["Retrieval Module (Search & Filtering)"]
        RET["RetrievalService<br/>- Apply topK & similarityThreshold<br/>- Filter by documentIds"]
        FILT["SearchFilterBuilder<br/>- Construct Spring AI filter expressions"]
    end

    subgraph ChatModule ["Chat & RAG Module (Prompt & Generation)"]
        PROMPT["PromptTemplates<br/>- Load system/user ST files<br/>- Number context blocks [1], [2]"]
        RAG["RagService<br/>- Evaluate retrieval results<br/>- Fast-refusal logic<br/>- Model execution"]
    end

    subgraph PersistenceLayer ["Persistence & External Services"]
        PGV[("pgvector Store<br/>- Cosine HNSW Index<br/>- 768-dim embeddings")]
        OLLAMA["Ollama Service<br/>- nomic-embed-text-v2-moe<br/>- qwen2.5:7b"]
    end

    API --> FS
    API --> REG
    FS --> HASH
    HASH --> READER
    READER --> CHUNK
    CHUNK --> ENRICH
    ENRICH --> PGV
    ENRICH --> OLLAMA

    API --> RAG
    RAG --> RET
    RET --> FILT
    FILT --> PGV
    PGV --> OLLAMA
    RAG --> PROMPT
    PROMPT --> OLLAMA
```

### 1.3 System Control Flow & Finite State Machine (FSM)

```mermaid
stateDiagram-v2
    [*] --> Idle

    state "Ingestion Pipeline FSM" as Ingestion {
        [*] --> FileReceived
        FileReceived --> Hashing: Compute SHA-256
        Hashing --> DuplicateCheck: Lookup Hash in DocumentRegistry
        
        DuplicateCheck --> SkippedDuplicate: Hash exists
        SkippedDuplicate --> [*]: Return duplicate=true

        DuplicateCheck --> Processing: New Hash
        Processing --> StoringFile: Save file to ./data/uploads/
        StoringFile --> Parsing: Tika DocumentReader
        Parsing --> Chunking: TokenTextSplitter
        Chunking --> Enriching: Attach metadata & UUIDs
        Enriching --> Vectorizing: Embed & insert pgvector
        
        Vectorizing --> IngestionFailed: Error occurs
        IngestionFailed --> [*]: Set Status = FAILED

        Vectorizing --> IngestionDone: Success
        IngestionDone --> [*]: Set Status = DONE, Return metadata
    }

    state "RAG Query Processing FSM" as RAGFlow {
        [*] --> QueryReceived
        QueryReceived --> Filtering: Parse optional documentIds
        Filtering --> VectorSearch: Execute Similarity Query (pgvector)
        VectorSearch --> EvaluatingResults: Check similarity threshold

        EvaluatingResults --> RefusalState: Max score < similarityThreshold
        RefusalState --> [*]: Return "I don't know" (LLM Bypassed)

        EvaluatingResults --> PromptConstruction: Score >= similarityThreshold
        PromptConstruction --> LLMGeneration: System Prompt + Context [1]..[N] -> qwen2.5
        LLMGeneration --> CitationMapping: Map output sources
        CitationMapping --> [*]: Return Grounded Answer + Citations
    }
```

### 1.4 Detailed Subsystem Separation Architecture

```mermaid
graph TD
    %% Subsystem 1: REST Module
    subgraph REST_Module ["1. Spring Boot REST Module"]
        DC["DocumentController<br/>• POST /api/documents<br/>• GET /api/documents<br/>• DELETE /api/documents/{id}"]
        SC["SearchController<br/>• GET /api/search?q=... (Debug)"]
        AC["AskController<br/>• POST /api/ask"]
        EC["EvaluationController<br/>• GET /api/eval/run"]
    end

    %% Subsystem 2: Ingestion System
    subgraph Ingestion_System ["2. Spring Boot Ingestion System"]
        IS["IngestionService<br/>(Pipeline Orchestration)"]
        FSS["FileStorageService<br/>• Saves to ./data/uploads/<br/>• Path validation"]
        DR["DocumentRegistry<br/>• JdbcTemplate DB Repository<br/>• Table: 'documents'"]
        CH["ContentHasher<br/>• SHA-256 Checksum<br/>• UUID Chunk Gen"]
        DRF["DocumentReaderFactory<br/>• Tika parsing (PDF/TXT/MD)"]
        CS["ChunkingService<br/>• TokenTextSplitter (size 300)"]
        ME["MetadataEnricher<br/>• docId, hash, index tags"]
    end

    %% Subsystem 3: RAG Chat AI Engine
    subgraph RAG_Chat_AI ["3. RAG Chat AI Engine"]
        RS["RagService<br/>(RAG Flow Orchestration)"]
        RETS["RetrievalService<br/>• Queries VectorStore<br/>• Similarity thresholding"]
        SFB["SearchFilterBuilder<br/>• documentId filter logic"]
        PT["PromptTemplates<br/>• System & User .st templates<br/>• Context numbering [1]..[N]"]
        CC["Spring AI ChatClient<br/>(Configured Ollama Bean)"]
    end

    %% Subsystem 4: Embedding Model Subsystem
    subgraph Embedding_Model ["4. Embedding Model Subsystem"]
        OEM["Ollama Embedding Model<br/>• nomic-embed-text-v2-moe<br/>• 768-Float Vector Generator"]
    end

    %% Subsystem 5: Vector DB Subsystem
    subgraph Vector_DB ["5. Vector DB Subsystem (pgvector)"]
        VS["Spring AI VectorStore<br/>(Abstractions & Queries)"]
        PGV_DB[("PostgreSQL + pgvector<br/>• Table: vector_store<br/>• HNSW Cosine Index")]
    end

    %% Subsystem 6: External LLM Service
    subgraph LLM_Service ["6. Ollama Local LLM"]
        QWEN["qwen2.5:7b Chat Model<br/>(Native Windows / GPU)"]
    end

    %% --- Connections & Data Flows ---

    %% Ingestion Flow Connections
    DC -->|1. Multipart File| IS
    IS -->|2. Raw Bytes| CH
    CH -->|3. Check SHA-256| DR
    IS -->|4. Store Original| FSS
    IS -->|5. Save Meta (PROCESSING)| DR
    IS -->|6. File Path| DRF
    DRF -->|7. Raw Text Docs| CS
    CS -->|8. Token Chunks| ME
    ME -->|9. Enriched Chunks| VS
    VS -->|10. Text for Vectorization| OEM
    OEM -->|11. 768-dim Embeddings| VS
    VS -->|12. SQL Batch Insert| PGV_DB
    IS -->|13. Mark Status DONE| DR

    %% Search & Debug Connections
    SC -->|Direct Vector Query| RETS

    %% RAG / Chat Flow Connections
    AC -->|1. AskRequest (Question + DocIDs)| RS
    RS -->|2. Retrieve Chunks| RETS
    RETS -->|3. Build Filters| SFB
    SFB -->|4. Filtered Query| VS
    VS -->|5. Embed Query Text| OEM
    OEM -->|6. Query Vector| VS
    VS -->|7. Similarity Search| PGV_DB
    PGV_DB -->>|8. Top-K Chunks + Scores| VS
    VS -->>|9. List<Document>| RETS
    RETS -->>|10. List<RetrievedChunk>| RS
    
    RS -->|11. Fast-Refusal Check| RS
    RS -->|12. Render Prompts & [1] Context| PT
    PT -->|13. Formatted Prompt| CC
    CC -->|14. Inference Request| QWEN
    QWEN -->>|15. Generated Answer String| CC
    CC -->>|16. Answer Text| RS
    RS -->|17. AskResponse (Answer + Sources)| AC
```

---

## 2. Component Architecture & Package Breakdown

Package root: `github.mralmostcool.chunk_norris`

### 2.1 Configuration Layer (`{pkg}.config`)
- **`RagProperties.java`**: Strongly-typed `@ConfigurationProperties(prefix = "rag")` holding system parameters (`chunkSize`, `topK`, `similarityThreshold`, `uploadDir`).
- **`ChatClientConfig.java`**: Configures the Spring AI `ChatClient.Builder` bean connected to local Ollama chat model.

### 2.2 Ingestion Module (`{pkg}.ingestion`)
Handles physical storage, parsing, chunking, hashing, metadata enrichment, and database tracking.
- **`DocumentController.java`**: REST endpoints (`POST /api/documents`, `GET /api/documents`, `DELETE /api/documents/{id}`) for file management.
- **`IngestionService.java`**: Orchestrates duplicate detection, storage, reading, chunking, metadata enrichment, vector indexing, and status updates.
- **`FileStorageService.java`**: Manages physical file persistence in `./data/uploads/`, filename sanitization, and file deletion.
- **`DocumentReaderFactory.java`**: Wraps Spring AI `TikaDocumentReader` to extract raw structured text `Document` objects from PDF, MD, TXT files.
- **`ChunkingService.java`**: Uses `TokenTextSplitter` configured via `RagProperties.chunkSize` to break raw documents into model-safe token blocks.
- **`ContentHasher.java`**: Generates SHA-256 byte hashes and deterministic `UUID` chunk identifiers via `UUID.nameUUIDFromBytes`.
- **`MetadataEnricher.java`**: Attaches standard audit and tracking keys (`documentId`, `filename`, `chunkIndex`, `fileHash`, `uploadedAt`) to each chunk.
- **`DocumentRegistry.java`**: Plain `JdbcTemplate` repository interacting with `documents` SQL table to track processing status (`PENDING`, `PROCESSING`, `DONE`, `FAILED`).

### 2.3 Retrieval Module (`{pkg}.retrieval`)
Isolates semantic vector search from LLM generation.
- **`SearchController.java`**: Debug endpoint (`GET /api/search?q=...`) to test pure vector retrieval without invoking LLM.
- **`RetrievalService.java`**: Queries `VectorStore` using `topK` and `similarityThreshold`. Optionally applies metadata filters when specific `documentIds` are provided.
- **`SearchFilterBuilder.java`**: Constructs Spring AI metadata filter expressions for `documentId in [...]`.

### 2.4 Chat & RAG Module (`{pkg}.chat`)
Handles prompt generation, grounded reasoning, citation mapping, and endpoint execution.
- **`AskController.java`**: Main REST API (`POST /api/ask`) accepting questions and optional document scope filters.
- **`RagService.java`**: Executes RAG workflow: calls `RetrievalService`, formats chunk context into numbered blocks `[1]`, constructs prompt via `PromptTemplates`, invokes `ChatClient`, and attaches `SourceReference` list.
- **`PromptTemplates.java`**: Loads and compiles external StringTemplate files (`prompts/rag-system.st`, `prompts/rag-user.st`).

### 2.5 Evaluation Module (`{pkg}.evaluation`)
Quantifies RAG performance.
- **`EvaluationController.java`**: Exposes `/api/eval/run` to trigger batch testing against `eval/questions.json`.
- **`EvaluationRunner.java`**: Measures hit rate, refusal accuracy, and latency.

### 2.6 Health & Infrastructure (`{pkg}.common`)
- **`GlobalExceptionHandler.java`**: `@RestControllerAdvice` mapping domain exceptions (`UnsupportedFileTypeException`, `DocumentNotFoundException`) to RFC 7807 `ProblemDetail`.
- **`OllamaHealthIndicator.java`**: Custom Spring Boot Actuator health check pinging local Ollama service.

---

## 3. Data Schema & Persistence

### 3.1 Document Registry Table (`documents`)
```sql
CREATE TABLE IF NOT EXISTS documents (
    id UUID PRIMARY KEY,
    filename TEXT NOT NULL,
    file_hash TEXT UNIQUE NOT NULL,
    status TEXT NOT NULL,
    chunk_count INT DEFAULT 0,
    stored_path TEXT NOT NULL,
    uploaded_at TIMESTAMPTZ NOT NULL
);
```

### 3.2 Vector Store Table (`vector_store`)
Managed automatically by Spring AI pgvector integration:
- `id`: UUID (Deterministic from file hash + chunk index)
- `content`: TEXT (chunk text snippet)
- `metadata`: JSONB (`documentId`, `filename`, `chunkIndex`, `fileHash`, `uploadedAt`)
- `embedding`: VECTOR(768) (`nomic-embed-text-v2-moe`) with Cosine distance index (HNSW).

---

## 4. Module Inter-Communication & Flow Rules

1. **Ingestion Loop**:
   `DocumentController` -> `IngestionService` -> `FileStorageService` & `DocumentRegistry` -> `DocumentReaderFactory` -> `ChunkingService` -> `MetadataEnricher` -> `VectorStore`
2. **Idempotency Rule**:
   If `ContentHasher.sha256(file)` matches an existing entry in `DocumentRegistry`, ingestion halts immediately and returns `duplicate: true`.
3. **Retrieval Refusal Rule**:
   If `RetrievalService` finds no chunks meeting `rag.similarity-threshold`, `RagService` returns fixed refusal message immediately without calling Ollama (saves GPU compute).
4. **Citation Contract**:
   Every context chunk supplied to LLM is tagged `[1]`, `[2]`. System prompt enforces returning bracketed citations matching items in `sources` response array.
