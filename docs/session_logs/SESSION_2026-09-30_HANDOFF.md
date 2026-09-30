# Session Handoff Log: 2026-09-30

## Overview
Completed **RAG-021** (File Storage Component) and **RAG-022** (Upload Validation).

---

## Accomplished in this Session

### 1. File Storage Component (RAG-021)
- Implemented `github.mralmostcool.chunk_norris.ingestion.storage.FileStorageService`:
  - Storage path structure: `./data/uploads/{docId}/{filename}` (configured via `RagProperties.uploadDir`).
  - Path sanitization: Strips directory traversal attempts (`../`, `..\\`), rejects illegal characters, null bytes, and verifies normalized path containment.
  - Recursive deletion: `delete(UUID docId)` safely deletes `{uploadDir}/{docId}` directory and all files within.
- Added custom exceptions:
  - `InvalidFilenameException` (extends `RagException`, HTTP 400).
  - `FileStorageException` (extends `RagException`, HTTP 500).

### 2. Upload Validation Component (RAG-022)
- Implemented `github.mralmostcool.chunk_norris.ingestion.validation.UploadValidator`:
  - Allow-list: `.pdf`, `.docx`, `.txt`, `.html`, `.htm`.
  - Content inspection via Apache Tika 3.3.1 (`tika.detect(...)`): checks true MIME types (`application/pdf`, docx openxml, `text/plain`, `text/html`) and detects spoofed extensions.
  - Max size enforcement: compares against `rag.max-upload-bytes` and throws `MaxUploadSizeExceededException` (mapped to HTTP 413 in `GlobalExceptionHandler`).
  - Empty file detection: throws `EmptyFileException` (extends `RagException`, HTTP 400).
- Configured default `rag:` properties block in `src/main/resources/application.yaml`.

### 3. Tests & Verification
- `FileStorageServiceTest`: 14 tests covering valid names, relative/Windows path traversals, empty/dot names, save operations, and recursive folder deletion.
- `UploadValidatorTest`: 8 tests covering PDF, DOCX, TXT, HTML, empty file, oversized file, disallowed extension, and spoofed file.
- Full Maven test suite: 51 tests run, 0 failures, 0 errors.

### 4. Git & Release Tracking
- Updated `CHANGELOG.md` under `[0.4.0-SNAPSHOT]`.
- Committed `08647fe: feat(ingestion): implement file storage and upload validation (RAG-021, RAG-022)`.
- Pushed to `origin/dev`.

---

## Next Steps / Immediate Backlog

1. **RAG-023: Checksum and duplicate detection**
   - Create SHA-256 calculation logic (`ChecksumCalculator` in `ingestion.logic`).
   - Query `DocumentRepository.findByChecksum(checksum)`.
   - If document exists with status `READY`: reject with HTTP 409 (`DuplicateDocumentException`).
   - If document exists with status `FAILED`: allow re-ingestion.

2. **RAG-024: Parsing with TikaDocumentReader**
   - Create `DocumentParser` in `ingestion.logic` wrapping `TikaDocumentReader`.
   - Return extracted text and metadata.
   - Handle empty or unparseable files with clear exception (mark document `FAILED`).
   - Add unit tests with fixtures (PDF, DOCX, TXT, HTML).

3. **RAG-025: Chunking with TokenTextSplitter**
   - Configure chunk size and overlap from `RagProperties`.
   - Attach metadata (`docId`, `filename`, `chunkIndex`, `page`).
