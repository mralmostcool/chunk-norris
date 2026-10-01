package github.mralmostcool.chunk_norris.ollama;

// ./mvnw.cmd test -Dtest=OllamaSmokeIntegrationTest

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
public class OllamaSmokeIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    @Autowired
    private ChatModel chatmodel;


    @Autowired
    private EmbeddingModel embeddingModel;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("Chat model responds to smoke prompt")
    void chatModelSmokeTest() {
        String response = chatmodel.call("say ok");
        assertThat(response)
                .as("Chat model returned null or empty response")
                .isNotNull();

    }

    @Test
    @DisplayName("Embedding output dimension matches vector_store column dimension")
    void embeddingDimensionMatchesDatabseColumn() {
        float[] embedding = embeddingModel.embed("smoke test string");

        int actualDim = embedding.length;

        Integer dbDim = jdbcTemplate.queryForObject(
                "SELECT atttypmod FROM pg_attribute WHERE attrelid = 'vector_store'::regclass AND attname = 'embedding'",
                Integer.class);
        assertThat(actualDim)
                .withFailMessage("Dimension mismatch: model produces %d, but vector_store column requires %d",
                        actualDim, dbDim)
                .isEqualTo(dbDim);
    }

}
