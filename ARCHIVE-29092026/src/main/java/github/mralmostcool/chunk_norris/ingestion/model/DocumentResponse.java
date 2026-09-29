package github.mralmostcool.chunk_norris.ingestion.model;

import java.time.Instant;
import java.util.UUID;

public record DocumentResponse(
        UUID id,
        String filename,
        String contentType,
        long sizeBytes,
        String checksum,
        int chunkCount,
        Instant uploadedAt,
        DocumentStatus status,
        String failureReason
) {
    public static DocumentResponse from(Document document) {
        return new DocumentResponse(
                document.id(),
                document.filename(),
                document.contentType(),
                document.sizeBytes(),
                document.checksum(),
                document.chunkCount(),
                document.uploadedAt(),
                document.status(),
                document.failureReason()
        );
    }
}
