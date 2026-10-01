package github.mralmostcool.chunk_norris.retrieval;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "rag.retrieval")
public record RetrievalConfig(
        @DefaultValue("4") int topK,
        @DefaultValue("0.5") double similarityThreshold) {

    public RetrievalConfig {
        if (topK <= 0) {
            topK = 4;
        }
        if (similarityThreshold <= 0.0) {
            similarityThreshold = 0.5;
        }
    }
}
