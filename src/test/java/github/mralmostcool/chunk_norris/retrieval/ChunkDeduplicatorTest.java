package github.mralmostcool.chunk_norris.retrieval;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChunkDeduplicatorTest {

    private final ChunkDeduplicator deduplicator = new ChunkDeduplicator();

    @Test
    @DisplayName("null or empty list returns empty list")
    void testNullOrEmpty() {
        assertThat(deduplicator.deduplicate(null)).isEmpty();
        assertThat(deduplicator.deduplicate(List.of())).isEmpty();
    }

    @Test
    @DisplayName("Single chunk is returned unchanged")
    void testSingleChunk() {
        RetrievedChunk chunk = new RetrievedChunk("chunk-1", "Unique text", Map.of(), 0.9);
        List<RetrievedChunk> result = deduplicator.deduplicate(List.of(chunk));
        assertThat(result).containsExactly(chunk);
    }

    @Test
    @DisplayName("Near-identical chunks with different case or punctuation are deduped, keeping highest score")
    void testNearIdenticalTextDedup() {
        RetrievedChunk lowerScore = new RetrievedChunk("chunk-low", "Spring Boot makes microservices fast and reliable.", Map.of(), 0.72);
        RetrievedChunk higherScore = new RetrievedChunk("chunk-high", "Spring Boot makes microservices FAST and reliable!!!", Map.of(), 0.94);

        // Pass lower score first
        List<RetrievedChunk> result = deduplicator.deduplicate(List.of(lowerScore, higherScore));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo("chunk-high");
        assertThat(result.get(0).score()).isEqualTo(0.94);
    }

    @Test
    @DisplayName("Overlapping chunks from adjacent windows are deduped, preserving highest score")
    void testAdjacentWindowOverlapDedup() {
        // Chunk A and Chunk B have ~80% word overlap from adjacent sliding windows
        String textWindow1 = "Docker containers isolate application processes with lightweight Linux namespaces and cgroups for efficiency.";
        String textWindow2 = "Application processes with lightweight Linux namespaces and cgroups for efficiency and resource management.";

        RetrievedChunk chunk1 = new RetrievedChunk("window-1", textWindow1, Map.of(), 0.82);
        RetrievedChunk chunk2 = new RetrievedChunk("window-2", textWindow2, Map.of(), 0.91);

        List<RetrievedChunk> result = deduplicator.deduplicate(List.of(chunk1, chunk2));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo("window-2");
        assertThat(result.get(0).score()).isEqualTo(0.91);
    }

    @Test
    @DisplayName("Distinct non-overlapping chunks are all retained in descending score order")
    void testDistinctChunksRetained() {
        RetrievedChunk chunkA = new RetrievedChunk("c1", "PostgreSQL pgvector extension provides cosine and euclidean distance index.", Map.of(), 0.85);
        RetrievedChunk chunkB = new RetrievedChunk("c2", "Sourdough bread starter ferments with wild lactobacillus bacteria cultures.", Map.of(), 0.78);

        List<RetrievedChunk> result = deduplicator.deduplicate(List.of(chunkB, chunkA));

        assertThat(result).hasSize(2);
        assertThat(result.get(0).id()).isEqualTo("c1");
        assertThat(result.get(1).id()).isEqualTo("c2");
    }
}
