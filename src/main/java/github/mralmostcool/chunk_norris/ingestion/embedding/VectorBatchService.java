package github.mralmostcool.chunk_norris.ingestion.embedding;

import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import github.mralmostcool.chunk_norris.ingestion.model.DocumentStatus;
import github.mralmostcool.chunk_norris.ingestion.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class VectorBatchService {

    private static final Logger log = LoggerFactory.getLogger(VectorBatchService.class);
    private static final int DEFAULT_BATCH_SIZE = 50;

    private final VectorStore vectorStore;
    private final DocumentRepository documentRepository;
    private final JdbcTemplate jdbcTemplate;

    public int storeChunks(UUID docId, List<Document> chunks) {
        return storeChunks(docId, chunks, DEFAULT_BATCH_SIZE);
    }

    public int storeChunks(UUID docId, List<Document> chunks, int batchSize) {
        if (docId == null) {
            throw new IllegalArgumentException("docId must not be null");
        }
        if (chunks == null || chunks.isEmpty()) {
            documentRepository.updateChunkCount(docId, 0);
            documentRepository.updateStatus(docId, DocumentStatus.READY);
            return 0;
        }

        int effectiveBatchSize = Math.max(1, batchSize);
        int totalChunks = chunks.size();

        try {
            for (int i = 0; i < totalChunks; i += effectiveBatchSize) {
                int end = Math.min(i + effectiveBatchSize, totalChunks);
                List<Document> batch = chunks.subList(i, end);
                log.debug("Adding vector batch for docId {} (chunks {} to {})", docId, i, end - 1);
                vectorStore.add(batch);
            }

            documentRepository.updateChunkCount(docId, totalChunks);
            documentRepository.updateStatus(docId, DocumentStatus.READY);
            return totalChunks;
        } catch (Exception e) {
            log.error("Failed to embed and store chunks for docId {}", docId, e);
            documentRepository.updateStatus(docId, DocumentStatus.FAILED, e.getMessage());
            throw e;
        }
    }

    public void deleteByDocId(UUID docId) {
        if (docId == null) {
            return;
        }
        try {
            Filter.Expression filter = new FilterExpressionBuilder()
                    .eq("docId", docId.toString())
                    .build();
            vectorStore.delete(filter);
        } catch (Exception e) {
            log.warn("Error while deleting vectors for docId {}", docId, e);
            throw e;
        }
    }

    public int countByDocId(UUID docId) {
        if (docId == null) {
            return 0;
        }
        String sql = "SELECT count(*) FROM vector_store WHERE metadata->>'docId' = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, docId.toString());
        return count != null ? count : 0;
    }
}
