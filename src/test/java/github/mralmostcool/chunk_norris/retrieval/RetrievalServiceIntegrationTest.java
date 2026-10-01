package github.mralmostcool.chunk_norris.retrieval;

import java.util.List;
import java.util.Map;

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
class RetrievalServiceIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    @Autowired
    private VectorStore vectorStore;

    @Autowired
    private RetrievalService retrievalService;

    private static final String DOC_ID = "seed-rag-041";

    @BeforeEach
    void seedChunks() {
        Document quantumDoc = Document.builder()
                .text("Quantum computing utilizes superposition and entanglement of qubits to perform calculations exponentially faster.")
                .metadata(Map.of("docId", DOC_ID, "topic", "quantum", "chunkIndex", 0))
                .build();

        Document pizzaDoc = Document.builder()
                .text("Neapolitan pizza crust requires high hydration dough fermented for 48 hours and baked in a wood-fired stone oven.")
                .metadata(Map.of("docId", DOC_ID, "topic", "pizza", "chunkIndex", 1))
                .build();

        vectorStore.add(List.of(quantumDoc, pizzaDoc));
    }

    @AfterEach
    void cleanUp() {
        try {
            vectorStore.delete(new FilterExpressionBuilder().eq("docId", DOC_ID).build());
        } catch (Exception ignored) {
        }
    }

    @Test
    @DisplayName("Relevant query returns hits; gibberish query returns empty list")
    void testRetrieveRelevantAndGibberish() {
        // 1. Relevant query should return matching hits
        List<RetrievedChunk> relevantHits = retrievalService.retrieve("quantum superposition qubits");
        assertThat(relevantHits)
                .as("Relevant query must return at least one chunk")
                .isNotEmpty();
        assertThat(relevantHits.get(0).text()).contains("Quantum computing utilizes superposition");
        assertThat(relevantHits.get(0).score()).isGreaterThanOrEqualTo(retrievalService.getRetrievalConfig().similarityThreshold());

        // 2. Gibberish query should return empty list (nothing passes threshold, no weak padding)
        List<RetrievedChunk> gibberishHits = retrievalService.retrieve("asdkfjlwefkjsd nvalskdjf zzzqqqxxx yyyzzz");
        assertThat(gibberishHits)
                .as("Gibberish query must return empty list without weak padding")
                .isEmpty();
    }
}
