package github.mralmostcool.chunk_norris.retrieval;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "spring.ai.vectorstore.pgvector.initialize-schema=true")
@Testcontainers
class MetadataFilterIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    @Autowired
    private VectorStore vectorStore;

    @Autowired
    private RetrievalService retrievalService;

    private final UUID docIdA = UUID.randomUUID();
    private final UUID docIdB = UUID.randomUUID();

    @BeforeEach
    void seedDocuments() {
        Document chunkA = Document.builder()
                .text("Relational databases use B-tree indexes to optimize range and equality queries.")
                .metadata(Map.of("docId", docIdA.toString(), "filename", "docA.txt", "chunkIndex", 0))
                .build();

        Document chunkB = Document.builder()
                .text("Relational databases use write-ahead logging to guarantee crash recovery and durability.")
                .metadata(Map.of("docId", docIdB.toString(), "filename", "docB.txt", "chunkIndex", 0))
                .build();

        vectorStore.add(List.of(chunkA, chunkB));
    }

    @AfterEach
    void cleanUp() {
        try {
            vectorStore.delete(new FilterExpressionBuilder().eq("docId", docIdA.toString()).build());
            vectorStore.delete(new FilterExpressionBuilder().eq("docId", docIdB.toString()).build());
        } catch (Exception ignored) {
        }
    }

    @Test
    @DisplayName("Metadata filter restricts results strictly to specified docIds")
    void testFilteringRestrictsResults() {
        // Query targeting docIdA only
        List<RetrievedChunk> resultsA = retrievalService.retrieve("Relational databases", List.of(docIdA));
        assertThat(resultsA).isNotEmpty();
        assertThat(resultsA).allMatch(chunk -> docIdA.toString().equals(chunk.metadata().get("docId")));
        assertThat(resultsA).noneMatch(chunk -> docIdB.toString().equals(chunk.metadata().get("docId")));

        // Query targeting docIdB only
        List<RetrievedChunk> resultsB = retrievalService.retrieve("Relational databases", List.of(docIdB));
        assertThat(resultsB).isNotEmpty();
        assertThat(resultsB).allMatch(chunk -> docIdB.toString().equals(chunk.metadata().get("docId")));
        assertThat(resultsB).noneMatch(chunk -> docIdA.toString().equals(chunk.metadata().get("docId")));

        // Query targeting both
        List<RetrievedChunk> resultsBoth = retrievalService.retrieve("Relational databases", List.of(docIdA, docIdB));
        assertThat(resultsBoth).hasSize(2);
    }
}
