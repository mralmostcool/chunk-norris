package github.mralmostcool.chunk_norris.ingestion.service;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import github.mralmostcool.chunk_norris.common.exceptions.DuplicateDocumentException;
import github.mralmostcool.chunk_norris.common.exceptions.RagException;
import github.mralmostcool.chunk_norris.ingestion.checksum.ChecksumService;
import github.mralmostcool.chunk_norris.ingestion.chunking.DocumentChunker;
import github.mralmostcool.chunk_norris.ingestion.embedding.VectorBatchService;
import github.mralmostcool.chunk_norris.ingestion.model.Document;
import github.mralmostcool.chunk_norris.ingestion.model.DocumentStatus;
import github.mralmostcool.chunk_norris.ingestion.parser.DocumentParser;
import github.mralmostcool.chunk_norris.ingestion.repository.DocumentRepository;
import github.mralmostcool.chunk_norris.ingestion.storage.FileStorageService;
import github.mralmostcool.chunk_norris.ingestion.validation.UploadValidator;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class IngestionService {

    private static final Logger log = LoggerFactory.getLogger(IngestionService.class);

    private final UploadValidator uploadValidator;
    private final ChecksumService checksumService;
    private final FileStorageService fileStorageService;
    private final DocumentParser documentParser;
    private final DocumentChunker documentChunker;
    private final VectorBatchService vectorBatchService;
    private final DocumentRepository documentRepository;

    public Document ingest(MultipartFile file) {
        // 1. Validate file
        uploadValidator.validate(file);

        // 2. Compute checksum
        String checksum = checksumService.calculateSha256(file);

        // 3. Duplicate detection: reject if already READY or PROCESSING
        Optional<Document> readyDoc = documentRepository.findByChecksumAndStatus(checksum, DocumentStatus.READY);
        if (readyDoc.isPresent()) {
            throw new DuplicateDocumentException(readyDoc.get().id().toString());
        }
        Optional<Document> processingDoc = documentRepository.findByChecksumAndStatus(checksum, DocumentStatus.PROCESSING);
        if (processingDoc.isPresent()) {
            throw new DuplicateDocumentException(processingDoc.get().id().toString());
        }

        // 4. Register document in PROCESSING state
        UUID docId = UUID.randomUUID();
        String filename = fileStorageService.sanitizeFilename(file.getOriginalFilename());
        String contentType = file.getContentType() != null ? file.getContentType() : "application/octet-stream";

        Document initialDoc = Document.builder()
                .id(docId)
                .filename(filename)
                .contentType(contentType)
                .sizeBytes(file.getSize())
                .checksum(checksum)
                .chunkCount(0)
                .status(DocumentStatus.PROCESSING)
                .uploadedAt(Instant.now())
                .build();

        documentRepository.insert(initialDoc);

        // 5. Ingestion pipeline with compensation
        try {
            // Save file
            Path savedPath = fileStorageService.save(docId, file);

            // Parse document
            List<org.springframework.ai.document.Document> parsedDocs = documentParser.parse(savedPath);

            // Chunk document
            List<org.springframework.ai.document.Document> chunks = documentChunker.chunk(docId, filename, parsedDocs);

            // Embed and store
            vectorBatchService.storeChunks(docId, chunks);

            return documentRepository.findById(docId)
                    .orElseThrow(() -> new IllegalStateException("Document not found after ingestion: " + docId));

        } catch (Exception e) {
            log.error("Ingestion failed for docId {}. Triggering compensation.", docId, e);
            compensate(docId, e.getMessage());
            if (e instanceof RagException ragException) {
                throw ragException;
            }
            throw new RuntimeException("Ingestion failed for document: " + filename, e);
        }
    }

    public void delete(UUID docId) {
        deleteDocument(docId);
    }

    public void deleteDocument(UUID docId) {
        if (docId == null) {
            throw new IllegalArgumentException("docId must not be null");
        }

        // 1. Verify document exists
        documentRepository.findById(docId)
                .orElseThrow(() -> new github.mralmostcool.chunk_norris.common.exceptions.DocumentNotFoundException(docId.toString()));

        // 2. Delete vectors first
        vectorBatchService.deleteByDocId(docId);

        // 3. Delete physical files
        fileStorageService.delete(docId);

        // 4. Delete document metadata row
        documentRepository.deleteById(docId);
    }

    public Document updateDocument(UUID docId, MultipartFile file) {
        if (docId == null) {
            throw new IllegalArgumentException("docId must not be null");
        }

        // 1. Verify document exists
        Document existing = documentRepository.findById(docId)
                .orElseThrow(() -> new github.mralmostcool.chunk_norris.common.exceptions.DocumentNotFoundException(docId.toString()));

        // 2. Validate new file
        uploadValidator.validate(file);

        // 3. Compute checksum
        String checksum = checksumService.calculateSha256(file);

        // 4. Duplicate check against other active documents
        Optional<Document> duplicateReady = documentRepository.findByChecksumAndStatus(checksum, DocumentStatus.READY);
        if (duplicateReady.isPresent() && !duplicateReady.get().id().equals(docId)) {
            throw new DuplicateDocumentException(duplicateReady.get().id().toString());
        }
        Optional<Document> duplicateProcessing = documentRepository.findByChecksumAndStatus(checksum, DocumentStatus.PROCESSING);
        if (duplicateProcessing.isPresent() && !duplicateProcessing.get().id().equals(docId)) {
            throw new DuplicateDocumentException(duplicateProcessing.get().id().toString());
        }

        // 5. Delete old vectors and files
        vectorBatchService.deleteByDocId(docId);
        fileStorageService.delete(docId);

        // 6. Mark PROCESSING
        documentRepository.updateStatus(docId, DocumentStatus.PROCESSING);

        String filename = fileStorageService.sanitizeFilename(file.getOriginalFilename());
        String contentType = file.getContentType() != null ? file.getContentType() : "application/octet-stream";

        try {
            // Save new file
            Path savedPath = fileStorageService.save(docId, file);

            // Re-parse
            List<org.springframework.ai.document.Document> parsedDocs = documentParser.parse(savedPath);

            // Re-chunk
            List<org.springframework.ai.document.Document> chunks = documentChunker.chunk(docId, filename, parsedDocs);

            // Re-embed and store
            vectorBatchService.storeChunks(docId, chunks);

            // Update document row with new metadata
            Document updatedDoc = existing.toBuilder()
                    .filename(filename)
                    .contentType(contentType)
                    .sizeBytes(file.getSize())
                    .checksum(checksum)
                    .chunkCount(chunks.size())
                    .status(DocumentStatus.READY)
                    .failureReason(null)
                    .uploadedAt(Instant.now())
                    .build();

            documentRepository.update(updatedDoc);

            return documentRepository.findById(docId)
                    .orElseThrow(() -> new IllegalStateException("Document not found after re-ingestion: " + docId));

        } catch (Exception e) {
            log.error("Update/re-ingestion failed for docId {}. Triggering compensation.", docId, e);
            compensate(docId, e.getMessage());
            if (e instanceof RagException ragException) {
                throw ragException;
            }
            throw new RuntimeException("Update/re-ingestion failed for document: " + filename, e);
        }
    }



    private void compensate(UUID docId, String failureReason) {
        // Compensate: remove partial vectors
        try {
            vectorBatchService.deleteByDocId(docId);
        } catch (Exception ex) {
            log.warn("Compensation: failed to delete vectors for docId {}", docId, ex);
        }

        // Compensate: remove physical files
        try {
            fileStorageService.delete(docId);
        } catch (Exception ex) {
            log.warn("Compensation: failed to delete storage directory for docId {}", docId, ex);
        }

        // Compensate: mark status FAILED
        try {
            documentRepository.updateStatus(docId, DocumentStatus.FAILED, failureReason);
        } catch (Exception ex) {
            log.warn("Compensation: failed to update status to FAILED for docId {}", docId, ex);
        }
    }
}
