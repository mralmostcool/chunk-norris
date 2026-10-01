package github.mralmostcool.chunk_norris.ingestion.repository;

import github.mralmostcool.chunk_norris.ingestion.model.Document;
import github.mralmostcool.chunk_norris.ingestion.model.DocumentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class DocumentRepositoryIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM documents");
    }

    @Test
    @DisplayName("insert and findById: document persists and reads back correctly")
    void insertAndFindById() {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        String checksum = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";

        Document document = Document.builder()
                .id(id)
                .filename("test-document.pdf")
                .contentType("application/pdf")
                .sizeBytes(1024L)
                .checksum(checksum)
                .chunkCount(0)
                .status(DocumentStatus.PROCESSING)
                .failureReason(null)
                .uploadedAt(now)
                .build();

        documentRepository.insert(document);

        Optional<Document> retrieved = documentRepository.findById(id);
        assertThat(retrieved).isPresent();

        Document doc = retrieved.get();
        assertThat(doc.id()).isEqualTo(id);
        assertThat(doc.filename()).isEqualTo("test-document.pdf");
        assertThat(doc.contentType()).isEqualTo("application/pdf");
        assertThat(doc.sizeBytes()).isEqualTo(1024L);
        assertThat(doc.checksum()).isEqualTo(checksum);
        assertThat(doc.chunkCount()).isZero();
        assertThat(doc.status()).isEqualTo(DocumentStatus.PROCESSING);
        assertThat(doc.failureReason()).isNull();
        assertThat(doc.uploadedAt()).isNotNull();
    }

    @Test
    @DisplayName("findAll: returns all documents ordered by uploadedAt descending")
    void findAll() {
        Instant t1 = Instant.now().minus(10, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.MICROS);
        Instant t2 = Instant.now().truncatedTo(ChronoUnit.MICROS);

        Document doc1 = Document.builder()
                .id(UUID.randomUUID())
                .filename("file1.txt")
                .contentType("text/plain")
                .sizeBytes(100L)
                .checksum("1111111111111111111111111111111111111111111111111111111111111111")
                .chunkCount(2)
                .status(DocumentStatus.READY)
                .uploadedAt(t1)
                .build();

        Document doc2 = Document.builder()
                .id(UUID.randomUUID())
                .filename("file2.txt")
                .contentType("text/plain")
                .sizeBytes(200L)
                .checksum("2222222222222222222222222222222222222222222222222222222222222222")
                .chunkCount(4)
                .status(DocumentStatus.READY)
                .uploadedAt(t2)
                .build();

        documentRepository.insert(doc1);
        documentRepository.insert(doc2);

        List<Document> all = documentRepository.findAll();
        assertThat(all).hasSize(2);
        assertThat(all.get(0).id()).isEqualTo(doc2.id());
        assertThat(all.get(1).id()).isEqualTo(doc1.id());
    }

    @Test
    @DisplayName("findByChecksum: returns document matching checksum, or empty if not found")
    void findByChecksum() {
        UUID id = UUID.randomUUID();
        String checksum = "abcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890";

        Document doc = Document.builder()
                .id(id)
                .filename("sample.pdf")
                .contentType("application/pdf")
                .sizeBytes(500L)
                .checksum(checksum)
                .chunkCount(1)
                .status(DocumentStatus.READY)
                .uploadedAt(Instant.now())
                .build();

        documentRepository.insert(doc);

        Optional<Document> match = documentRepository.findByChecksum(checksum);
        assertThat(match).isPresent();
        assertThat(match.get().id()).isEqualTo(id);

        Optional<Document> missing = documentRepository
                .findByChecksum("0000000000000000000000000000000000000000000000000000000000000000");
        assertThat(missing).isEmpty();
    }

    @Test
    @DisplayName("updateStatus: updates document status and failure reason")
    void updateStatus() {
        UUID id = UUID.randomUUID();
        Document doc = Document.builder()
                .id(id)
                .filename("doc.docx")
                .contentType("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                .sizeBytes(2048L)
                .checksum("3333333333333333333333333333333333333333333333333333333333333333")
                .chunkCount(0)
                .status(DocumentStatus.PROCESSING)
                .uploadedAt(Instant.now())
                .build();

        documentRepository.insert(doc);

        int updated = documentRepository.updateStatus(id, DocumentStatus.READY);
        assertThat(updated).isEqualTo(1);

        Optional<Document> afterReady = documentRepository.findById(id);
        assertThat(afterReady).isPresent();
        assertThat(afterReady.get().status()).isEqualTo(DocumentStatus.READY);
        assertThat(afterReady.get().failureReason()).isNull();

        documentRepository.updateStatus(id, DocumentStatus.FAILED, "Parsing failed");
        Optional<Document> afterFailed = documentRepository.findById(id);
        assertThat(afterFailed).isPresent();
        assertThat(afterFailed.get().status()).isEqualTo(DocumentStatus.FAILED);
        assertThat(afterFailed.get().failureReason()).isEqualTo("Parsing failed");
    }

    @Test
    @DisplayName("updateChunkCount: updates chunk_count field")
    void updateChunkCount() {
        UUID id = UUID.randomUUID();
        Document doc = Document.builder()
                .id(id)
                .filename("doc.txt")
                .contentType("text/plain")
                .sizeBytes(300L)
                .checksum("4444444444444444444444444444444444444444444444444444444444444444")
                .chunkCount(0)
                .status(DocumentStatus.PROCESSING)
                .uploadedAt(Instant.now())
                .build();

        documentRepository.insert(doc);

        int updated = documentRepository.updateChunkCount(id, 8);
        assertThat(updated).isEqualTo(1);

        Optional<Document> updatedDoc = documentRepository.findById(id);
        assertThat(updatedDoc).isPresent();
        assertThat(updatedDoc.get().chunkCount()).isEqualTo(8);
    }

    @Test
    @DisplayName("deleteById: removes document and returns deleted count")
    void deleteById() {
        UUID id = UUID.randomUUID();
        Document doc = Document.builder()
                .id(id)
                .filename("temp.pdf")
                .contentType("application/pdf")
                .sizeBytes(123L)
                .checksum("5555555555555555555555555555555555555555555555555555555555555555")
                .chunkCount(1)
                .status(DocumentStatus.READY)
                .uploadedAt(Instant.now())
                .build();

        documentRepository.insert(doc);
        assertThat(documentRepository.findById(id)).isPresent();

        int deleted = documentRepository.deleteById(id);
        assertThat(deleted).isEqualTo(1);

        assertThat(documentRepository.findById(id)).isEmpty();
    }

    @Test
    @DisplayName("findByChecksumAndStatus: finds document by checksum and status")
    void findByChecksumAndStatus() {
        String checksum = "6666666666666666666666666666666666666666666666666666666666666666";
        Document docReady = Document.builder()
                .id(UUID.randomUUID())
                .filename("ready.pdf")
                .contentType("application/pdf")
                .sizeBytes(200L)
                .checksum(checksum)
                .chunkCount(2)
                .status(DocumentStatus.READY)
                .uploadedAt(Instant.now())
                .build();
        documentRepository.insert(docReady);

        Optional<Document> foundReady = documentRepository.findByChecksumAndStatus(checksum, DocumentStatus.READY);
        assertThat(foundReady).isPresent();
        assertThat(foundReady.get().status()).isEqualTo(DocumentStatus.READY);

        Optional<Document> foundProcessing = documentRepository.findByChecksumAndStatus(checksum, DocumentStatus.PROCESSING);
        assertThat(foundProcessing).isEmpty();
    }

    @Test
    @DisplayName("duplicate checksum allowed when previous document status is FAILED")
    void allowDuplicateChecksumForFailedDoc() {
        String checksum = "7777777777777777777777777777777777777777777777777777777777777777";
        Document failedDoc = Document.builder()
                .id(UUID.randomUUID())
                .filename("failed.pdf")
                .contentType("application/pdf")
                .sizeBytes(100L)
                .checksum(checksum)
                .chunkCount(0)
                .status(DocumentStatus.FAILED)
                .failureReason("Corrupt file")
                .uploadedAt(Instant.now().minusSeconds(60))
                .build();
        documentRepository.insert(failedDoc);

        Document newDoc = Document.builder()
                .id(UUID.randomUUID())
                .filename("retry.pdf")
                .contentType("application/pdf")
                .sizeBytes(100L)
                .checksum(checksum)
                .chunkCount(0)
                .status(DocumentStatus.PROCESSING)
                .uploadedAt(Instant.now())
                .build();

        Document inserted = documentRepository.insert(newDoc);
        assertThat(inserted).isNotNull();
        assertThat(documentRepository.findById(newDoc.id())).isPresent();
    }
}

