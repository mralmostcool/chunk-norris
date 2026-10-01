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
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.ai.vectorstore.pgvector.initialize-schema=true")
@AutoConfigureMockMvc
@Testcontainers
class DebugRetrievalIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    @Autowired
    private MockMvc mvc;

    @Autowired
    private VectorStore vectorStore;

    private final UUID docId = UUID.randomUUID();

    @BeforeEach
    void seedData() {
        Document doc = Document.builder()
                .text("Chunk Norris implements retrieval augmented generation using pgvector and nomic embeddings.")
                .metadata(Map.of("docId", docId.toString(), "filename", "test-doc.txt", "chunkIndex", 0))
                .build();
        vectorStore.add(List.of(doc));
    }

    @AfterEach
    void cleanUp() {
        try {
            vectorStore.delete(new FilterExpressionBuilder().eq("docId", docId.toString()).build());
        } catch (Exception ignored) {
        }
    }

    @Test
    @DisplayName("POST /api/debug/retrieve returns chunks and similarity scores for seeded content")
    void testDebugRetrieveEndpoint() throws Exception {
        String requestJson = """
                {
                    "query": "retrieval augmented generation chunk norris",
                    "docIds": ["%s"],
                    "topK": 3,
                    "similarityThreshold": 0.4
                }
                """.formatted(docId);

        mvc.perform(post("/api/debug/retrieve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].text").value("Chunk Norris implements retrieval augmented generation using pgvector and nomic embeddings."))
                .andExpect(jsonPath("$[0].score", greaterThan(0.4)))
                .andExpect(jsonPath("$[0].citationIndex", is(1)));
    }
}
