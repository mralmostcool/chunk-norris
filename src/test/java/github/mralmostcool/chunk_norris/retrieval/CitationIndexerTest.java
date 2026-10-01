package github.mralmostcool.chunk_norris.retrieval;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CitationIndexerTest {

    private final CitationIndexer indexer = new CitationIndexer();

    @Test
    @DisplayName("null or empty list returns empty list")
    void testNullOrEmpty() {
        assertThat(indexer.assignCitationIndices(null)).isEmpty();
        assertThat(indexer.assignCitationIndices(List.of())).isEmpty();
    }

    @Test
    @DisplayName("Single chunk receives citation index [1]")
    void testSingleChunk() {
        RetrievedChunk chunk = new RetrievedChunk("id-1", "Some text", Map.of("docId", "123"), 0.95);
        List<RetrievedChunk> result = indexer.assignCitationIndices(List.of(chunk));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).citationIndex()).isEqualTo(1);
        assertThat(result.get(0).id()).isEqualTo("id-1");
        assertThat(result.get(0).text()).isEqualTo("Some text");
        assertThat(result.get(0).metadata()).containsEntry("docId", "123");
        assertThat(result.get(0).score()).isEqualTo(0.95);
    }

    @Test
    @DisplayName("Assigns contiguous [1]..[N] indices in ranked order")
    void testContiguousRanking() {
        RetrievedChunk c1 = new RetrievedChunk("id-1", "Top match", Map.of(), 0.95);
        RetrievedChunk c2 = new RetrievedChunk("id-2", "Second match", Map.of(), 0.88);
        RetrievedChunk c3 = new RetrievedChunk("id-3", "Third match", Map.of(), 0.74);

        List<RetrievedChunk> results = indexer.assignCitationIndices(List.of(c1, c2, c3));

        assertThat(results).hasSize(3);
        assertThat(results.get(0).citationIndex()).isEqualTo(1);
        assertThat(results.get(0).id()).isEqualTo("id-1");

        assertThat(results.get(1).citationIndex()).isEqualTo(2);
        assertThat(results.get(1).id()).isEqualTo("id-2");

        assertThat(results.get(2).citationIndex()).isEqualTo(3);
        assertThat(results.get(2).id()).isEqualTo("id-3");
    }

    @Test
    @DisplayName("Index assignment is stable across multiple invocations")
    void testStability() {
        RetrievedChunk c1 = new RetrievedChunk("id-1", "Chunk A", Map.of(), 0.91);
        RetrievedChunk c2 = new RetrievedChunk("id-2", "Chunk B", Map.of(), 0.84);
        List<RetrievedChunk> input = List.of(c1, c2);

        List<RetrievedChunk> run1 = indexer.assignCitationIndices(input);
        List<RetrievedChunk> run2 = indexer.assignCitationIndices(input);

        assertThat(run1).isEqualTo(run2);
        assertThat(run1.get(0).citationIndex()).isEqualTo(run2.get(0).citationIndex()).isEqualTo(1);
        assertThat(run1.get(1).citationIndex()).isEqualTo(run2.get(1).citationIndex()).isEqualTo(2);
    }
}
