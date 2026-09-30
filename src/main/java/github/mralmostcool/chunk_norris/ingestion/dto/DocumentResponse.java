package github.mralmostcool.chunk_norris.ingestion.dto;

import java.time.Instant;
import java.util.UUID;

import github.mralmostcool.chunk_norris.ingestion.model.Document;
import github.mralmostcool.chunk_norris.ingestion.model.DocumentStatus;

public record DocumentResponse(
        UUID id,
        String filename,
        String contentType,
        long sizeBytes,
        String checksum,
        int chunkCount,
        DocumentStatus status,
        String failureReason,
        Instant uploadedAt) {

    public static DocumentResponse from(Document document) {
        return new DocumentResponse(
                document.id(),
                document.filename(),
                document.contentType(),
                document.sizeBytes(),
                document.checksum(),
                document.chunkCount(),
                document.status(),
                document.failureReason(),
                document.uploadedAt());
    }

}
