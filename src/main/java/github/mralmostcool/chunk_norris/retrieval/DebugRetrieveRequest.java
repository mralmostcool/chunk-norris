package github.mralmostcool.chunk_norris.retrieval;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;

public record DebugRetrieveRequest(
        @NotBlank(message = "query must not be blank")
        String query,
        List<UUID> docIds,
        Integer topK,
        Double similarityThreshold) {
}
