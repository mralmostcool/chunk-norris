package github.mralmostcool.chunk_norris.ingestion.embedding;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import github.mralmostcool.chunk_norris.ingestion.model.DocumentStatus;
import github.mralmostcool.chunk_norris.ingestion.repository.DocumentRepository;

@SpringBootTest
@Testcontainers
class VectorBatchServiceIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    @Autowired
    private VectorBatchService vectorBatchService;

    @Autowired
    private DocumentRepository documentRepository;

    @Test
    @DisplayName("Embed and store chunks: status is READY and count in vector_store equals chunkCount")
    void embedAndStoreChunks_verifiesCountAndStatus() {
        UUID docId = UUID.randomUUID();
        github.mralmostcool.chunk_norris.ingestion.model.Document doc = github.mralmostcool.chunk_norris.ingestion.model.Document.builder()
                .id(docId)
                .filename("integration-doc.txt")
                .contentType("text/plain")
                .sizeBytes(100L)
                .checksum("8888888888888888888888888888888888888888888888888888888888888888")
                .chunkCount(0)
                .status(DocumentStatus.PROCESSING)
                .uploadedAt(Instant.now())
                .build();
        documentRepository.insert(doc);

        List<Document> chunks = List.of(
                Document.builder().text("chunk one content").metadata("docId", docId.toString()).metadata("chunkIndex", 0).build(),
                Document.builder().text("chunk two content").metadata("docId", docId.toString()).metadata("chunkIndex", 1).build());

        int stored = vectorBatchService.storeChunks(docId, chunks, 1);
        assertThat(stored).isEqualTo(2);

        // Verify SELECT count(*) FROM vector_store WHERE metadata->>'docId' = ? equals chunkCount
        int vectorCount = vectorBatchService.countByDocId(docId);
        assertThat(vectorCount).isEqualTo(2);

        // Verify repository row
        var updatedDoc = documentRepository.findById(docId);
        assertThat(updatedDoc).isPresent();
        assertThat(updatedDoc.get().status()).isEqualTo(DocumentStatus.READY);
        assertThat(updatedDoc.get().chunkCount()).isEqualTo(2);

        // Clean up
        vectorBatchService.deleteByDocId(docId);
        assertThat(vectorBatchService.countByDocId(docId)).isEqualTo(0);
    }
}
