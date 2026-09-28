package github.mralmostcool.chunk_norris.health;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
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

    @InjectMocks
    private OllamaHealthIndicator healthIndicator;

    @Test
    void health_whenOllamaReachable_returnsUpWithModels() {
        OllamaApi.Model model = mock(OllamaApi.Model.class);
        when(model.name()).thenReturn("qwen2.5:7b");
        OllamaApi.ListModelResponse response = new OllamaApi.ListModelResponse(List.of(model));
        when(ollamaApi.listModels()).thenReturn(response);

        Health health = healthIndicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("ollama", "reachable");
        assertThat(health.getDetails()).containsEntry("models", List.of("qwen2.5:7b"));
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
