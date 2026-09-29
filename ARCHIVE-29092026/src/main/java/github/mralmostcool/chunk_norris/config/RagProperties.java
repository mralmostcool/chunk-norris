package github.mralmostcool.chunk_norris.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "rag")
public record RagProperties(
                int chunkSize,
                int chunkOverlap,
                int topK,
                double similarityThreshold,
                int memoryWindow,
                long maxUploadBytes,
                String uploadDir) {
}