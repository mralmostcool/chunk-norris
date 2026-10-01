package github.mralmostcool.chunk_norris.retrieval;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import static org.assertj.core.api.Assertions.assertThat;

class RetrievedChunkTest {

    @Test
    @DisplayName("Constructs chunk with all fields and maintains immutable metadata")
    void testConstructorAndImmutability() {
        Map<String, Object> meta = new HashMap<>();
        meta.put("docId", "123");
        meta.put("page", 2);

        RetrievedChunk chunk = new RetrievedChunk("chunk-1", "Sample text", meta, 0.88, 1);

        assertThat(chunk.id()).isEqualTo("chunk-1");
        assertThat(chunk.text()).isEqualTo("Sample text");
        assertThat(chunk.metadata()).containsEntry("docId", "123").containsEntry("page", 2);
        assertThat(chunk.score()).isEqualTo(0.88);
        assertThat(chunk.citationIndex()).isEqualTo(1);

        meta.put("docId", "mutated");
        assertThat(chunk.metadata().get("docId")).isEqualTo("123");
    }

    @Test
    @DisplayName("Convenience constructors initialize defaults correctly")
    void testConvenienceConstructors() {
        RetrievedChunk chunk1 = new RetrievedChunk("text only", Map.of("k", "v"), 0.75);
        assertThat(chunk1.id()).isNull();
        assertThat(chunk1.text()).isEqualTo("text only");
        assertThat(chunk1.score()).isEqualTo(0.75);
        assertThat(chunk1.citationIndex()).isNull();

        RetrievedChunk chunk2 = new RetrievedChunk("id-2", "text 2", Map.of(), 0.90);
        assertThat(chunk2.id()).isEqualTo("id-2");
        assertThat(chunk2.citationIndex()).isNull();

        RetrievedChunk chunk3 = new RetrievedChunk("text 3", Map.of(), 0.65, 3);
        assertThat(chunk3.citationIndex()).isEqualTo(3);
    }

    @Test
    @DisplayName("fromDocument maps Spring AI Document properties to RetrievedChunk")
    void testFromDocument() {
        Document doc = Document.builder()
                .text("Spring AI document content")
                .metadata(Map.of("docId", "doc-xyz", "page", 1))
                .score(0.92)
                .build();

        RetrievedChunk chunk = RetrievedChunk.fromDocument(doc);

        assertThat(chunk).isNotNull();
        assertThat(chunk.id()).isEqualTo(doc.getId());
        assertThat(chunk.text()).isEqualTo("Spring AI document content");
        assertThat(chunk.score()).isEqualTo(0.92);
        assertThat(chunk.metadata()).containsEntry("docId", "doc-xyz");
        assertThat(chunk.citationIndex()).isNull();

        assertThat(RetrievedChunk.fromDocument(null)).isNull();
    }

    @Test
    @DisplayName("withCitationIndex creates new instance with updated citation index")
    void testWithCitationIndex() {
        RetrievedChunk original = new RetrievedChunk("id-1", "content", Map.of("docId", "1"), 0.82);
        RetrievedChunk indexed = original.withCitationIndex(1);

        assertThat(indexed.citationIndex()).isEqualTo(1);
        assertThat(indexed.id()).isEqualTo(original.id());
        assertThat(indexed.text()).isEqualTo(original.text());
        assertThat(indexed.score()).isEqualTo(original.score());
        assertThat(indexed.metadata()).isEqualTo(original.metadata());
        assertThat(original.citationIndex()).isNull();
    }
}
