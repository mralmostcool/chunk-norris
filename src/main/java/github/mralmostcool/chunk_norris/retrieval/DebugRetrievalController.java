package github.mralmostcool.chunk_norris.retrieval;

import java.util.List;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/debug/retrieve")
@Tag(name = "Debug Retrieval", description = "Temporary debug endpoint for inspecting retrieval results and scores")
public class DebugRetrievalController {

    private final RetrievalService retrievalService;

    public DebugRetrievalController(RetrievalService retrievalService) {
        this.retrievalService = retrievalService;
    }

    @PostMapping
    @Operation(summary = "Retrieve chunks with scores and citation indices for debugging and parameter tuning")
    public List<RetrievedChunk> retrieve(@Valid @RequestBody DebugRetrieveRequest request) {
        return retrievalService.retrieve(
                request.query(),
                request.docIds(),
                request.topK(),
                request.similarityThreshold());
    }
}
