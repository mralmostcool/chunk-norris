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

@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.flyway.locations=classpath:db/migration"
})
@Testcontainers
class DocumentRepositoryIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    @Autowired
    private DocumentRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired(required = false)
    private org.flywaydb.core.Flyway flyway;

    @BeforeEach
    void setUp() {
        if (flyway != null) {
            flyway.migrate();
        } else {
            org.flywaydb.core.Flyway.configure()
                    .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                    .locations("classpath:db/migration")
                    .cleanDisabled(false)
                    .load()
                    .migrate();
        }
        jdbcTemplate.execute("DELETE FROM documents");
    }

    private Document createSampleDocument(String filename, String checksum) {
        return Document.builder()
                .id(UUID.randomUUID())
                .filename(filename)
                .contentType("application/pdf")
                .sizeBytes(2048L)
                .checksum(checksum)
                .chunkCount(0)
                .status(DocumentStatus.PROCESSING)
                .uploadedAt(Instant.now().truncatedTo(ChronoUnit.MICROS))
                .build();
    }

    @Test
    @DisplayName("insert and findById should store and retrieve document")
    void insertAndFindById() {
        Document doc = createSampleDocument("sample.pdf", "a".repeat(64));
        Document saved = repository.insert(doc);

        Optional<Document> retrieved = repository.findById(saved.id());

        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().id()).isEqualTo(saved.id());
        assertThat(retrieved.get().filename()).isEqualTo("sample.pdf");
        assertThat(retrieved.get().contentType()).isEqualTo("application/pdf");
        assertThat(retrieved.get().sizeBytes()).isEqualTo(2048L);
        assertThat(retrieved.get().checksum()).isEqualTo("a".repeat(64));
        assertThat(retrieved.get().chunkCount()).isEqualTo(0);
        assertThat(retrieved.get().status()).isEqualTo(DocumentStatus.PROCESSING);
        assertThat(retrieved.get().failureReason()).isNull();
    }

    @Test
    @DisplayName("findAll should return all documents ordered by uploaded_at descending")
    void findAll() {
        Document doc1 = createSampleDocument("doc1.pdf", "1".repeat(64))
                .toBuilder()
                .uploadedAt(Instant.now().minus(2, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MICROS))
                .build();
        Document doc2 = createSampleDocument("doc2.pdf", "2".repeat(64))
                .toBuilder()
                .uploadedAt(Instant.now().minus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MICROS))
                .build();

        repository.insert(doc1);
        repository.insert(doc2);

        List<Document> all = repository.findAll();

        assertThat(all).hasSize(2);
        assertThat(all.get(0).filename()).isEqualTo("doc2.pdf");
        assertThat(all.get(1).filename()).isEqualTo("doc1.pdf");
    }

    @Test
    @DisplayName("findByChecksum should find document with matching checksum")
    void findByChecksum() {
        String checksum = "b".repeat(64);
        Document doc = createSampleDocument("search.pdf", checksum);
        repository.insert(doc);

        Optional<Document> found = repository.findByChecksum(checksum);
        Optional<Document> notFound = repository.findByChecksum("c".repeat(64));

        assertThat(found).isPresent();
        assertThat(found.get().filename()).isEqualTo("search.pdf");
        assertThat(notFound).isEmpty();
    }

    @Test
    @DisplayName("updateStatus should update status and failure reason")
    void updateStatus() {
        Document doc = createSampleDocument("status_test.pdf", "d".repeat(64));
        Document saved = repository.insert(doc);

        int updated = repository.updateStatus(saved.id(), DocumentStatus.FAILED, "Parsing failed");

        assertThat(updated).isEqualTo(1);
        Document afterUpdate = repository.findById(saved.id()).orElseThrow();
        assertThat(afterUpdate.status()).isEqualTo(DocumentStatus.FAILED);
        assertThat(afterUpdate.failureReason()).isEqualTo("Parsing failed");

        repository.updateStatus(saved.id(), DocumentStatus.READY);
        Document afterSuccess = repository.findById(saved.id()).orElseThrow();
        assertThat(afterSuccess.status()).isEqualTo(DocumentStatus.READY);
        assertThat(afterSuccess.failureReason()).isNull();
    }

    @Test
    @DisplayName("updateChunkCount should update chunk count")
    void updateChunkCount() {
        Document doc = createSampleDocument("chunk_test.pdf", "e".repeat(64));
        Document saved = repository.insert(doc);

        int updated = repository.updateChunkCount(saved.id(), 42);

        assertThat(updated).isEqualTo(1);
        Document afterUpdate = repository.findById(saved.id()).orElseThrow();
        assertThat(afterUpdate.chunkCount()).isEqualTo(42);
    }

    @Test
    @DisplayName("deleteById should delete document and return true if existed")
    void deleteById() {
        Document doc = createSampleDocument("delete_test.pdf", "f".repeat(64));
        Document saved = repository.insert(doc);

        boolean deleted = repository.deleteById(saved.id());
        boolean deletedAgain = repository.deleteById(saved.id());

        assertThat(deleted).isTrue();
        assertThat(deletedAgain).isFalse();
        assertThat(repository.findById(saved.id())).isEmpty();
    }
}
