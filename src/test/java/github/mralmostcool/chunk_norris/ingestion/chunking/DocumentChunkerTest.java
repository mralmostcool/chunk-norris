package github.mralmostcool.chunk_norris.ingestion.chunking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import github.mralmostcool.chunk_norris.config.RagProperties;

class DocumentChunkerTest {

    @Test
    @DisplayName("Chunking with known input asserts chunk count, overlap, and all four metadata keys")
    void chunk_withKnownInputAndOverlap() {
        // chunkSize = 10, chunkOverlap = 2
        RagProperties props = new RagProperties(10, 2, 4, 0.5, 10, 20971520L, "data/uploads");
        DocumentChunker chunker = new DocumentChunker(props);

        // 26 words
        String text = "one two three four five six seven eight nine ten eleven twelve thirteen fourteen fifteen sixteen seventeen eighteen nineteen twenty twenty-one twenty-two twenty-three twenty-four twenty-five twenty-six";

        UUID docId = UUID.randomUUID();
        String filename = "sample.txt";
        Document sourceDoc = Document.builder()
                .text(text)
                .metadata("page_number", 3)
                .build();

        List<Document> chunks = chunker.chunk(docId, filename, List.of(sourceDoc));

        // 1. Assert chunk count
        assertThat(chunks).isNotEmpty();
        assertThat(chunks.size()).isGreaterThanOrEqualTo(2);

        // 2. Assert overlap is respected between adjacent chunks
        Document first = chunks.get(0);
        Document second = chunks.get(1);
        // Overlap means words from end of first chunk appear at beginning of second chunk
        String[] firstWords = first.getText().trim().split("\\s+");
        String lastWordOfFirst = firstWords[firstWords.length - 1];
        assertThat(second.getText()).contains(lastWordOfFirst);

        // 3. Assert every chunk carries all four metadata keys: docId, filename, chunkIndex, page
        for (int i = 0; i < chunks.size(); i++) {
            Document chunk = chunks.get(i);
            Map<String, Object> meta = chunk.getMetadata();

            assertThat(meta).containsKey("docId");
            assertThat(meta.get("docId")).isEqualTo(docId.toString());

            assertThat(meta).containsKey("filename");
            assertThat(meta.get("filename")).isEqualTo(filename);

            assertThat(meta).containsKey("chunkIndex");
            assertThat(meta.get("chunkIndex")).isEqualTo(i);

            assertThat(meta).containsKey("page");
            assertThat(meta.get("page")).isEqualTo(3);
        }
    }

    @Test
    @DisplayName("Small document under chunk size produces single chunk with all metadata")
    void chunk_smallDocument_singleChunk() {
        RagProperties props = new RagProperties(300, 50, 4, 0.5, 10, 20971520L, "data/uploads");
        DocumentChunker chunker = new DocumentChunker(props);

        UUID docId = UUID.randomUUID();
        Document doc = Document.builder()
                .text("Short document content.")
                .build();

        List<Document> chunks = chunker.chunk(docId, "short.txt", List.of(doc));

        assertThat(chunks).hasSize(1);
        Document chunk = chunks.get(0);
        assertThat(chunk.getMetadata()).containsEntry("docId", docId.toString());
        assertThat(chunk.getMetadata()).containsEntry("filename", "short.txt");
        assertThat(chunk.getMetadata()).containsEntry("chunkIndex", 0);
        assertThat(chunk.getMetadata()).containsEntry("page", 1);
    }

    @Test
    @DisplayName("Null or empty inputs validation")
    void chunk_validation() {
        RagProperties props = new RagProperties(300, 50, 4, 0.5, 10, 20971520L, "data/uploads");
        DocumentChunker chunker = new DocumentChunker(props);

        assertThatThrownBy(() -> chunker.chunk(null, "f.txt", List.of()))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> chunker.chunk(UUID.randomUUID(), "", List.of()))
                .isInstanceOf(IllegalArgumentException.class);

        List<Document> emptyResult = chunker.chunk(UUID.randomUUID(), "f.txt", List.of());
        assertThat(emptyResult).isEmpty();
    }
}
