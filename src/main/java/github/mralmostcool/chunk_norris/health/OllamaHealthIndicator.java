package github.mralmostcool.chunk_norris.health;

import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class OllamaHealthIndicator implements HealthIndicator {

    private final OllamaApi ollamaApi;

    public OllamaHealthIndicator(OllamaApi ollamaApi) {
        this.ollamaApi = ollamaApi;
    }

    @Override
    public Health health() {
        try {
            OllamaApi.ListModelResponse response = ollamaApi.listModels();
            List<String> models = (response != null && response.models() != null)
                    ? response.models().stream().map(OllamaApi.Model::name).toList()
                    : List.of();
            return Health.up()
                    .withDetail("ollama", "reachable")
                    .withDetail("models", models)
                    .build();
        } catch (Exception e) {
            return Health.down(e)
                    .withDetail("ollama", "unreachable")
                    .withDetail("error", e.getMessage())
                    .build();
        }
    }
}
