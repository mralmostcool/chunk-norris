package github.mralmostcool.chunk_norris.health;

import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class OllamaHealthIndicator implements HealthIndicator {
    private final OllamaApi ollamaApi;
    private final String chatModel;
    private final String embeddingModel;

    public OllamaHealthIndicator(
            OllamaApi ollamaApi,
            @Value("${spring.ai.ollama.chat.options.model:qwen2.5:7b}") String chatModel,
            @Value("${spring.ai.ollama.embedding.options.model:nomic-embed-text-v2-moe}") String embeddingModel) {
        this.ollamaApi = ollamaApi;
        this.chatModel = chatModel;
        this.embeddingModel = embeddingModel;
    }

    @Override
    @SuppressWarnings("null")
    public Health health() {
        try {
            OllamaApi.ListModelResponse response = ollamaApi.listModels();
            List<String> availableModels = (response != null && response.models() != null)
                    ? response.models().stream().map(OllamaApi.Model::name).toList()
                    : List.of();
            List<String> missingModels = new ArrayList<>();
            if (!isModelPresent(availableModels, chatModel)) {
                missingModels.add(chatModel);
            }
            if (!isModelPresent(availableModels, embeddingModel)) {
                missingModels.add(embeddingModel);
            }
            if (!missingModels.isEmpty()) {
                return Health.down()
                        .withDetail("ollama", "reachable")
                        .withDetail("models", availableModels)
                        .withDetail("missingModels", missingModels)
                        .withDetail("error", "Configured models missing: " + String.join(", ", missingModels))
                        .build();
            }
            return Health.up()
                    .withDetail("ollama", "reachable")
                    .withDetail("models", availableModels)
                    .withDetail("chatModel", chatModel)
                    .withDetail("embeddingModel", embeddingModel)
                    .build();
        } catch (Exception e) {
            return Health.down(e)
                    .withDetail("ollama", "unreachable")
                    .withDetail("error", e.getMessage())
                    .build();
        }
    }

    private boolean isModelPresent(List<String> availableModels, String targetModel) {
        if (targetModel == null || targetModel.isBlank()) {
            return true;
        }
        String normalizedTarget = normalize(targetModel);
        return availableModels.stream().anyMatch(available -> {
            if (available == null) {
                return false;
            }
            if (available.equalsIgnoreCase(targetModel)) {
                return true;
            }
            return normalize(available).equalsIgnoreCase(normalizedTarget);
        });
    }

    private String normalize(String modelName) {
        return modelName.endsWith(":latest")
                ? modelName.substring(0, modelName.length() - 7)
                : modelName;
    }
}
