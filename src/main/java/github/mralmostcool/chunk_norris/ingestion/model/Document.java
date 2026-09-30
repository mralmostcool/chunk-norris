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
        DocumentStatus status,
        String failureReason,
        Instant uploadedAt) {

    public Document(
            UUID id,
            String filename,
            String contentType,
            long sizeBytes,
            String checksum,
            int chunkCount,
            DocumentStatus status,
            String failureReason,
            Instant uploadedAt) {
        this.id = id;
        this.filename = filename;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.checksum = checksum;
        this.chunkCount = chunkCount;
        this.status = status;
        this.failureReason = failureReason;
        this.uploadedAt = uploadedAt;
    }

}
