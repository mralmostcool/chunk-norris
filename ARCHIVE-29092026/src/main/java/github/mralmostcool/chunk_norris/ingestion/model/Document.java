package github.mralmostcool.chunk_norris.ingestion.model;

import java.time.Instant;
import java.util.UUID;

import lombok.Builder;

@Builder(toBuilder = true)
public record Document(
        UUID id,
        String filename,
        String contentType,
        long sizeBytes,
        String checksum,
        int chunkCount,
        Instant uploadedAt,
        DocumentStatus status,
        String failureReason) {
    public Document(
            UUID id,
            String filename,
            String contentType,
            long sizeBytes,
            String checksum,
            int chunkCount,
            Instant uploadedAt,
            DocumentStatus status) {
        this(id, filename, contentType, sizeBytes, checksum, chunkCount, uploadedAt, status, null);
    }
}