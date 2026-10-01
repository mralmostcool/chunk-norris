package github.mralmostcool.chunk_norris.ingestion.repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import github.mralmostcool.chunk_norris.ingestion.model.Document;
import github.mralmostcool.chunk_norris.ingestion.model.DocumentStatus;
import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class DocumentRepository {

    private final JdbcTemplate jdbcTemplate;

    private static final RowMapper<Document> ROW_MAPPER = (rs, rowNum) -> new Document(
            UUID.fromString(rs.getString("id")),
            rs.getString("filename"),
            rs.getString("content_type"),
            rs.getLong("size_bytes"),
            rs.getString("checksum"),
            rs.getInt("chunk_count"),
            DocumentStatus.valueOf(rs.getString("status")),
            rs.getString("failure_reason"),
            rs.getTimestamp("uploaded_at").toInstant());

    public Document insert(Document document) {
        UUID id = document.id() != null ? document.id() : UUID.randomUUID();
        Instant uploadedAt = document.uploadedAt() != null ? document.uploadedAt() : Instant.now();
        String sql = """
                INSERT INTO documents (id, filename, content_type, size_bytes, checksum, chunk_count, status, failure_reason, uploaded_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        jdbcTemplate.update(
                sql,
                id,
                document.filename(),
                document.contentType(),
                document.sizeBytes(),
                document.checksum(),
                document.chunkCount(),
                document.status().name(),
                document.failureReason(),
                Timestamp.from(uploadedAt));

        return document.toBuilder()
                .id(id)
                .uploadedAt(uploadedAt)
                .build();
    }

    public List<Document> findAll() {
        String sql = """
                SELECT id, filename, content_type, size_bytes, checksum, chunk_count, status, failure_reason, uploaded_at
                FROM documents
                ORDER BY uploaded_at DESC
                """;
        return jdbcTemplate.query(sql, ROW_MAPPER);
    }

    public Optional<Document> findById(UUID id) {
        String sql = """
                SELECT id, filename, content_type, size_bytes, checksum, chunk_count, status, failure_reason, uploaded_at
                FROM documents
                WHERE id = ?
                """;
        return jdbcTemplate.query(sql, ROW_MAPPER, id).stream().findFirst();
    }

    public Optional<Document> findByChecksum(String checksum) {
        String sql = """
                SELECT id, filename, content_type, size_bytes, checksum, chunk_count, status, failure_reason, uploaded_at
                FROM documents
                WHERE checksum = ?
                """;
        return jdbcTemplate.query(sql, ROW_MAPPER, checksum).stream().findFirst();
    }

    public Optional<Document> findByChecksumAndStatus(String checksum, DocumentStatus status) {
        String sql = """
                SELECT id, filename, content_type, size_bytes, checksum, chunk_count, status, failure_reason, uploaded_at
                FROM documents
                WHERE checksum = ? AND status = ?
                ORDER BY uploaded_at DESC
                LIMIT 1
                """;
        return jdbcTemplate.query(sql, ROW_MAPPER, checksum, status.name()).stream().findFirst();
    }

    public int updateStatus(UUID id, DocumentStatus status) {
        return updateStatus(id, status, null);
    }

    public int updateStatus(UUID id, DocumentStatus status, String failureReason) {
        String sql = """
                UPDATE documents
                SET status = ?, failure_reason = ?
                WHERE id = ?
                """;
        return jdbcTemplate.update(sql, status.name(), failureReason, id);
    }

    public int updateChunkCount(UUID id, int chunkCount) {
        String sql = """
                UPDATE documents
                SET chunk_count = ?
                WHERE id = ?
                """;
        return jdbcTemplate.update(sql, chunkCount, id);
    }

    public int deleteById(UUID id) {
        String sql = """
                DELETE FROM documents
                WHERE id = ?
                """;
        return jdbcTemplate.update(sql, id);
    }

    public int update(Document document) {
        String sql = """
                UPDATE documents
                SET filename = ?, content_type = ?, size_bytes = ?, checksum = ?, chunk_count = ?, status = ?, failure_reason = ?, uploaded_at = ?
                WHERE id = ?
                """;
        return jdbcTemplate.update(
                sql,
                document.filename(),
                document.contentType(),
                document.sizeBytes(),
                document.checksum(),
                document.chunkCount(),
                document.status().name(),
                document.failureReason(),
                Timestamp.from(document.uploadedAt() != null ? document.uploadedAt() : Instant.now()),
                document.id());
    }


}
