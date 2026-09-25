# Chunk Norris Architecture v2: Conversational RAG with Stateful Memory

## Overview

Chunk Norris v2 extends the core RAG architecture with **Stateful Multi-Turn Conversation Memory** and **Contextual Query Rewriting**. This enables interactive chat sessions where follow-up questions (e.g., *"What were its main features?"*) are dynamically re-contextualized using past conversation history before executing vector searches.

---

## 1. System Architecture Overview (with Conversation Memory)

### 1.1 Tiered Architecture with Chat Memory & Query Rewriter

```mermaid
graph TB
    subgraph Tier1 ["Tier 1: Client & REST API Gateway"]
        direction LR
        EP_DOC["/api/documents<br/>(Upload, List, Delete)"]
        EP_CHAT["/api/chat/conversations<br/>(Create Session, Send Message, History, Clear)"]
        EP_EVAL["/api/eval/run<br/>(Quality Harness)"]
        EP_ACT["/actuator/health<br/>(Ollama & DB Health)"]
    end

    subgraph Tier2 ["Tier 2: Core Domain & Memory Orchestration Services"]
        INGEST_SRV["Ingestion Service<br/>(File Parsing, Chunking & Vector Ingestion)"]
        CHAT_ORCH["Conversational RAG Service<br/>(Multi-turn Dialogue Manager)"]
        MEM_SRV["Chat Memory Service<br/>(Sliding Window Buffer & Session Management)"]
        REWRITE_SRV["Query Rewriter Service<br/>(History-Aware Search Query Condenser)"]
        RETR_SRV["Retrieval Service<br/>(Filtered Vector Search)"]
    end

    subgraph Tier3 ["Tier 3: Stateful Storage & Processing Engines"]
        SESSION_REG["Chat Session Repository<br/>(JdbcTemplate -> 'chat_sessions' / 'chat_messages')"]
        PARSER["Tika Document Reader & Splitter"]
        PROMPTS["Conversational Prompt Engine<br/>(System, History & Context Template Assembly)"]
    end

    subgraph Tier4 ["Tier 4: Spring AI & Framework Abstractions"]
        VS_ABS["Spring AI VectorStore Interface"]
        CC_ABS["Spring AI ChatClient Interface"]
        MEM_ABS["Spring AI ChatMemory Abstraction<br/>(Advisor Pipeline Integration)"]
        JDBC_ABS["Spring JdbcTemplate"]
    end

    subgraph Tier5 ["Tier 5: Infrastructure & Persistence"]
        direction LR
        PG_DB[("PostgreSQL 16 + pgvector<br/>• Tables: documents, vector_store<br/>• Tables: chat_sessions, chat_messages")]
        OLLAMA_LLM["Ollama Local Runtime<br/>• qwen2.5:7b (Chat & Rewriter)<br/>• nomic-embed-text-v2-moe"]
        DISK_STORAGE[("Local Disk Storage<br/>• ./data/uploads/<br/>• ./data/postgres/")]
    end

    %% Client Connections
    EP_DOC --> INGEST_SRV
    EP_CHAT --> CHAT_ORCH

    %% Conversational Flow
    CHAT_ORCH --> MEM_SRV
    MEM_SRV --> SESSION_REG
    CHAT_ORCH --> REWRITE_SRV
    REWRITE_SRV --> CC_ABS
    REWRITE_SRV --> RETR_SRV
    RETR_SRV --> VS_ABS
    CHAT_ORCH --> PROMPTS
    PROMPTS --> CC_ABS

    %% Ingestion Flow
    INGEST_SRV --> PARSER
    PARSER --> VS_ABS
    INGEST_SRV --> JDBC_ABS

    %% Framework to DB / Model Connections
    VS_ABS --> PG_DB
    SESSION_REG --> JDBC_ABS
    JDBC_ABS --> PG_DB
    CC_ABS --> OLLAMA_LLM
```

---

## 2. Multi-Turn Conversational RAG Sequence Workflow

```mermaid
sequenceDiagram
    autonumber
    actor User
    participant Controller as ChatSessionController
    participant Memory as ChatMemoryService
    participant Rewriter as QueryRewriterService
    participant Retrieval as RetrievalService
    participant VectorStore as pgvector Store
    participant LLM as Ollama ChatClient
    participant SessionDB as Chat Session SQL Store

    User->>Controller: POST /api/chat/conversations/{id}/messages<br/>{"message": "What were its key performance metrics?"}
    
    %% Step 1: Load Conversation Memory
    Controller->>Memory: getConversationHistory(conversationId, maxMessages=6)
    Memory->>SessionDB: SELECT * FROM chat_messages WHERE conversation_id = ? ORDER BY created_at DESC LIMIT 6
    SessionDB-->>Memory: List<ChatMessage> (Past Questions & Answers)
    Memory-->>Controller: Active Context Window History

    %% Step 2: Query Rewriting
    alt History is Non-Empty
        Controller->>Rewriter: rewriteQuery(rawQuestion, history)
        Rewriter->>LLM: Prompt LLM to rephrase "What were its key performance metrics?"<br/>using past topic (e.g. "Chunk Norris Benchmarks")
        LLM-->>Rewriter: Standalone Query: "Chunk Norris benchmark key performance metrics"
        Rewriter-->>Controller: Standalone Search Query
    else History Empty
        Rewriter-->>Controller: Original rawQuestion
    end

    %% Step 3: Retrieval with Standalone Query
    Controller->>Retrieval: retrieve(standaloneQuery, optionalDocFilters)
    Retrieval->>VectorStore: Similarity Search (topK, threshold, metadata filter)
    VectorStore-->>Retrieval: List<RetrievedChunk>
    Retrieval-->>Controller: List<RetrievedChunk>

    %% Step 4: RAG Prompting & Execution
    alt Relevant Chunks Found
        Controller->>LLM: Formatted Prompt [System Prompt + History Buffer + [1]..[N] Chunks + Question]
        LLM-->>Controller: Grounded Response + Citations
    else No Chunks Pass Threshold
        Controller->>Controller: Fallback Refusal ("I don't have enough context...")
    end

    %% Step 5: Save State
    Controller->>Memory: appendMessagePair(conversationId, userMsg, assistantMsg)
    Memory->>SessionDB: INSERT INTO chat_messages (id, conversation_id, role, content, created_at)
    
    Controller-->>User: ChatResponse { conversationId, answer, sources, historySnippet }
```

---

## 3. Database Schema Extensions for Conversation State

### 3.1 Chat Sessions Table (`chat_sessions`)
```sql
CREATE TABLE IF NOT EXISTS chat_sessions (
    id UUID PRIMARY KEY,
    title TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);
```

### 3.2 Chat Messages Table (`chat_messages`)
```sql
CREATE TABLE IF NOT EXISTS chat_messages (
    id UUID PRIMARY KEY,
    conversation_id UUID NOT NULL REFERENCES chat_sessions(id) ON DELETE CASCADE,
    role VARCHAR(20) NOT NULL, -- 'USER', 'ASSISTANT', 'SYSTEM'
    content TEXT NOT NULL,
    citations_json TEXT,       -- Serialized List<SourceReference>
    created_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_chat_messages_conv ON chat_messages(conversation_id, created_at DESC);
```

---

## 4. Module Breakdown (`{pkg}.chat.memory`)

### 4.1 `ChatSessionController.java`
- `POST /api/chat/conversations`: Initializes new chat session UUID.
- `POST /api/chat/conversations/{id}/messages`: Submits new turn, triggers rewrite $\rightarrow$ retrieve $\rightarrow$ generate $\rightarrow$ persist loop.
- `GET /api/chat/conversations/{id}/history`: Returns full transcript for frontend rendering.
- `DELETE /api/chat/conversations/{id}`: Deletes chat session and associated messages.

### 4.2 `ChatMemoryService.java`
Manages conversation buffer window (e.g. last 6-10 messages). Integrates with Spring AI's `ChatMemory` interface or implements `MessageWindowChatMemory`.

### 4.3 `QueryRewriterService.java`
Constructs a lightweight prompt instructing Ollama to condense ambiguous user follow-ups into explicit, standalone search vectors:
> *"Given the following conversation history and a follow-up question, rephrase the follow-up question to be a self-contained search query."*

### 4.4 `ConversationalRagService.java`
Coordinates history retrieval, query rewriter execution, document context injection, LLM generation, and persistent message store updates.
