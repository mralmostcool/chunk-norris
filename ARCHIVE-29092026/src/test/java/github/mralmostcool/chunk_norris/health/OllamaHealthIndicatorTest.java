package github.mralmostcool.chunk_norris.health;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OllamaHealthIndicatorTest {

    @Mock
    private OllamaApi ollamaApi;

    private OllamaHealthIndicator healthIndicator;

    @BeforeEach
    void setUp() {
        healthIndicator = new OllamaHealthIndicator(ollamaApi, "qwen2.5:7b", "nomic-embed-text-v2-moe");
    }

    @Test
    void health_whenOllamaReachableAndBothModelsPresent_returnsUp() {
        OllamaApi.Model chat = mock(OllamaApi.Model.class);
        when(chat.name()).thenReturn("qwen2.5:7b");

        OllamaApi.Model embed = mock(OllamaApi.Model.class);
        when(embed.name()).thenReturn("nomic-embed-text-v2-moe:latest");

        OllamaApi.ListModelResponse response = new OllamaApi.ListModelResponse(List.of(chat, embed));
        when(ollamaApi.listModels()).thenReturn(response);

        Health health = healthIndicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("ollama", "reachable");
        assertThat(health.getDetails()).containsEntry("models",
                List.of("qwen2.5:7b", "nomic-embed-text-v2-moe:latest"));
        assertThat(health.getDetails()).containsEntry("chatModel", "qwen2.5:7b");
        assertThat(health.getDetails()).containsEntry("embeddingModel", "nomic-embed-text-v2-moe");
    }

    @Test
    void health_whenModelMissing_returnsDown() {
        OllamaApi.Model chat = mock(OllamaApi.Model.class);
        when(chat.name()).thenReturn("qwen2.5:7b");

        OllamaApi.ListModelResponse response = new OllamaApi.ListModelResponse(List.of(chat));
        when(ollamaApi.listModels()).thenReturn(response);

        Health health = healthIndicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("ollama", "reachable");
        assertThat(health.getDetails()).containsEntry("missingModels", List.of("nomic-embed-text-v2-moe"));
        assertThat(health.getDetails().get("error").toString()).contains("nomic-embed-text-v2-moe");
    }

    @Test
    void health_whenOllamaThrows_returnsDownWithError() {
        when(ollamaApi.listModels()).thenThrow(new RuntimeException("Connection refused"));

        Health health = healthIndicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("ollama", "unreachable");
        assertThat(health.getDetails()).containsEntry("error", "Connection refused");
    }
}
