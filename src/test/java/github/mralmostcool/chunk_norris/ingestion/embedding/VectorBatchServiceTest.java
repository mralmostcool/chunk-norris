package github.mralmostcool.chunk_norris.ingestion.embedding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.jdbc.core.JdbcTemplate;

import github.mralmostcool.chunk_norris.ingestion.model.DocumentStatus;
import github.mralmostcool.chunk_norris.ingestion.repository.DocumentRepository;

@ExtendWith(MockitoExtension.class)
class VectorBatchServiceTest {

    @Mock
    private VectorStore vectorStore;

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private JdbcTemplate jdbcTemplate;

    private VectorBatchService vectorBatchService;

    @BeforeEach
    void setUp() {
        vectorBatchService = new VectorBatchService(vectorStore, documentRepository, jdbcTemplate);
    }

    @Test
    @DisplayName("storeChunks batches chunks and marks document READY on success")
    void storeChunks_batchesAndMarksReady() {
        UUID docId = UUID.randomUUID();
        List<Document> chunks = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            chunks.add(Document.builder().text("chunk " + i).metadata("docId", docId.toString()).build());
        }

        // Batch size = 2 -> 5 chunks split into batches of 2, 2, 1 (3 calls)
        int stored = vectorBatchService.storeChunks(docId, chunks, 2);

        assertThat(stored).isEqualTo(5);
        verify(vectorStore, times(3)).add(anyList());
        verify(documentRepository).updateChunkCount(docId, 5);
        verify(documentRepository).updateStatus(docId, DocumentStatus.READY);
    }

    @Test
    @DisplayName("storeChunks on error marks document FAILED and rethrows")
    void storeChunks_onError_marksFailed() {
        UUID docId = UUID.randomUUID();
        List<Document> chunks = List.of(
                Document.builder().text("c1").metadata("docId", docId.toString()).build());

        doThrow(new RuntimeException("Embedding timeout")).when(vectorStore).add(anyList());

        assertThatThrownBy(() -> vectorBatchService.storeChunks(docId, chunks, 10))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Embedding timeout");

        verify(documentRepository).updateStatus(eq(docId), eq(DocumentStatus.FAILED), eq("Embedding timeout"));
    }

    @Test
    @DisplayName("deleteByDocId executes vector store delete filter")
    void deleteByDocId_invokesVectorStoreDelete() {
        UUID docId = UUID.randomUUID();
        vectorBatchService.deleteByDocId(docId);

        verify(vectorStore).delete(any(Filter.Expression.class));
    }

    @Test
    @DisplayName("countByDocId queries vector_store metadata")
    void countByDocId_queriesDatabase() {
        UUID docId = UUID.randomUUID();
        when(jdbcTemplate.queryForObject(any(String.class), eq(Integer.class), eq(docId.toString())))
                .thenReturn(7);

        int count = vectorBatchService.countByDocId(docId);
        assertThat(count).isEqualTo(7);
    }
}
