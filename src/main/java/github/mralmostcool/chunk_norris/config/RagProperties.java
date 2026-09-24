package github.mralmostcool.chunk_norris.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "rag")
public record RagProperties(
                int chunkSize,
                int topK,
                double similarityThreshold,
                String uploadDir) {

}
