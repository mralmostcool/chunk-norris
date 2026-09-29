package github.mralmostcool.chunk_norris.ingestion.repository;

import github.mralmostcool.chunk_norris.ingestion.model.Document;
import github.mralmostcool.chunk_norris.ingestion.model.DocumentStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class DocumentRepository {

    private final JdbcTemplate jdbcTemplate;

    private static final RowMapper<Document> ROW_MAPPER = (rs, rowNum) -> Document.builder()
            .id(rs.getObject("id", UUID.class))
            .filename(rs.getString("filename"))
            .contentType(rs.getString("content_type"))
            .sizeBytes(rs.getLong("size_bytes"))
            .checksum(rs.getString("checksum") != null ? rs.getString("checksum").trim() : null)
            .chunkCount(rs.getInt("chunk_count"))
            .uploadedAt(rs.getTimestamp("uploaded_at") != null ? rs.getTimestamp("uploaded_at").toInstant() : null)
            .status(DocumentStatus.valueOf(rs.getString("status")))
            .failureReason(rs.getString("failure_reason"))
            .build();

    public Document insert(Document document) {
        String sql = """
                INSERT INTO documents (id, filename, content_type, size_bytes, checksum, chunk_count, status, failure_reason, uploaded_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        UUID id = document.id() != null ? document.id() : UUID.randomUUID();
        Instant uploadedAt = document.uploadedAt() != null ? document.uploadedAt() : Instant.now();
        Document documentToSave = document.toBuilder()
                .id(id)
                .uploadedAt(uploadedAt)
                .build();

        jdbcTemplate.update(
                sql,
                documentToSave.id(),
                documentToSave.filename(),
                documentToSave.contentType(),
                documentToSave.sizeBytes(),
                documentToSave.checksum(),
                documentToSave.chunkCount(),
                documentToSave.status().name(),
                documentToSave.failureReason(),
                Timestamp.from(documentToSave.uploadedAt()));

        return documentToSave;
    }

    public List<Document> findAll() {
        String sql = "SELECT id, filename, content_type, size_bytes, checksum, chunk_count, status, failure_reason, uploaded_at FROM documents ORDER BY uploaded_at DESC";
        return jdbcTemplate.query(sql, ROW_MAPPER);
    }

    public Optional<Document> findById(UUID id) {
        String sql = "SELECT id, filename, content_type, size_bytes, checksum, chunk_count, status, failure_reason, uploaded_at FROM documents WHERE id = ?";
        return jdbcTemplate.query(sql, ROW_MAPPER, id).stream().findFirst();
    }

    public Optional<Document> findByChecksum(String checksum) {
        String sql = "SELECT id, filename, content_type, size_bytes, checksum, chunk_count, status, failure_reason, uploaded_at FROM documents WHERE checksum = ?";
        return jdbcTemplate.query(sql, ROW_MAPPER, checksum).stream().findFirst();
    }

    public int updateStatus(UUID id, DocumentStatus status) {
        return updateStatus(id, status, null);
    }

    public int updateStatus(UUID id, DocumentStatus status, String failureReason) {
        String sql = "UPDATE documents SET status = ?, failure_reason = ? WHERE id = ?";
        return jdbcTemplate.update(sql, status.name(), failureReason, id);
    }

    public int updateChunkCount(UUID id, int chunkCount) {
        String sql = "UPDATE documents SET chunk_count = ? WHERE id = ?";
        return jdbcTemplate.update(sql, chunkCount, id);
    }

    public boolean deleteById(UUID id) {
        String sql = "DELETE FROM documents WHERE id = ?";
        return jdbcTemplate.update(sql, id) > 0;
    }

}
