package github.mralmostcool.chunk_norris.retrieval;

import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.stereotype.Service;

@Service
public class RetrievalService {

    private static final Logger log = LoggerFactory.getLogger(RetrievalService.class);

    private final VectorStore vectorStore;
    private final RetrievalConfig retrievalConfig;

    public RetrievalService(VectorStore vectorStore, RetrievalConfig retrievalConfig) {
        this.vectorStore = vectorStore;
        this.retrievalConfig = retrievalConfig;
    }

    public List<RetrievedChunk> retrieve(String query) {
        return retrieve(query, (Filter.Expression) null, null, null);
    }

    public List<RetrievedChunk> retrieve(String query, Filter.Expression docFilters) {
        return retrieve(query, docFilters, null, null);
    }

    public List<RetrievedChunk> retrieve(
            String query,
            Filter.Expression docFilters,
            Integer topK,
            Double similarityThreshold) {

        if (query == null || query.isBlank()) {
            return Collections.emptyList();
        }

        int effectiveTopK = (topK != null && topK > 0) ? topK : retrievalConfig.topK();
        double effectiveThreshold = (similarityThreshold != null && similarityThreshold >= 0.0)
                ? similarityThreshold
                : retrievalConfig.similarityThreshold();

        SearchRequest.Builder builder = SearchRequest.builder()
                .query(query)
                .topK(effectiveTopK)
                .similarityThreshold(effectiveThreshold);

        if (docFilters != null) {
            builder.filterExpression(docFilters);
        }

        SearchRequest request = builder.build();
        List<Document> documents = vectorStore.similaritySearch(request);

        if (documents == null || documents.isEmpty()) {
            log.debug("No documents returned by vector store for query: '{}'", query);
            return Collections.emptyList();
        }

        List<RetrievedChunk> results = documents.stream()
                .filter(doc -> doc != null && (doc.getScore() == null || doc.getScore() >= effectiveThreshold))
                .map(RetrievedChunk::fromDocument)
                .toList();

        log.debug("Retrieved {} chunks meeting threshold {} for query: '{}'",
                results.size(), effectiveThreshold, query);
        return results;
    }

    public RetrievalConfig getRetrievalConfig() {
        return retrievalConfig;
    }
}
