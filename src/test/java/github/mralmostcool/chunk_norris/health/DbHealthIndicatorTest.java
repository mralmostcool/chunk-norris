package github.mralmostcool.chunk_norris.health;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DbHealthIndicatorTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private DbHealthIndicator healthIndicator;

    @Test
    void health_whenDbAndPgvectorAvailable_returnsUp() {
        when(jdbcTemplate.queryForObject(eq("SELECT 1"), eq(Integer.class))).thenReturn(1);
        when(jdbcTemplate.query(anyString(), ArgumentMatchers.<RowMapper<String>>any()))
                .thenReturn(List.of("0.7.0"));

        Health health = healthIndicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("database", "PostgreSQL");
        assertThat(health.getDetails()).containsEntry("validationQuery", "SELECT 1");
        assertThat(health.getDetails()).containsEntry("pgvector", "0.7.0");
    }

    @Test
    void health_whenPgvectorMissing_returnsDown() {
        when(jdbcTemplate.queryForObject(eq("SELECT 1"), eq(Integer.class))).thenReturn(1);
        when(jdbcTemplate.query(anyString(), ArgumentMatchers.<RowMapper<String>>any()))
                .thenReturn(List.of());

        Health health = healthIndicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("database", "PostgreSQL");
        assertThat(health.getDetails()).containsEntry("pgvector", "missing");
        assertThat(health.getDetails()).containsEntry("error", "pgvector extension is not installed");
    }

    @Test
    void health_whenDbThrows_returnsDown() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class)))
                .thenThrow(new RuntimeException("Connection error"));

        Health health = healthIndicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("database", "PostgreSQL");
        assertThat(health.getDetails()).containsEntry("error", "Connection error");
    }
}
