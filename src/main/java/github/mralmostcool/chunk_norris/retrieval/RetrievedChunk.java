package github.mralmostcool.chunk_norris.retrieval;

import java.util.Collections;
import java.util.Map;

import org.springframework.ai.document.Document;

public record RetrievedChunk(
        String id,
        String text,
        Map<String, Object> metadata,
        Double score,
        Integer citationIndex) {

    public RetrievedChunk {
        metadata = metadata != null ? Map.copyOf(metadata) : Collections.emptyMap();
    }

    public RetrievedChunk(String text, Map<String, Object> metadata, Double score) {
        this(null, text, metadata, score, null);
    }

    public RetrievedChunk(String id, String text, Map<String, Object> metadata, Double score) {
        this(id, text, metadata, score, null);
    }

    public RetrievedChunk(String text, Map<String, Object> metadata, Double score, Integer citationIndex) {
        this(null, text, metadata, score, citationIndex);
    }

    public static RetrievedChunk fromDocument(Document document) {
        if (document == null) {
            return null;
        }
        return new RetrievedChunk(
                document.getId(),
                document.getText(),
                document.getMetadata(),
                document.getScore(),
                null);
    }

    public RetrievedChunk withCitationIndex(Integer index) {
        return new RetrievedChunk(this.id, this.text, this.metadata, this.score, index);
    }
}
