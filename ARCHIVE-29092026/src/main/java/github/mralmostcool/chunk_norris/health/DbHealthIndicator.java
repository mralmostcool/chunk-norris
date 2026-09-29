package github.mralmostcool.chunk_norris.health;

import lombok.RequiredArgsConstructor;

import java.util.List;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DbHealthIndicator implements HealthIndicator {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public Health health() {
        try {

            Integer ping = jdbcTemplate.queryForObject("SELECT 1", Integer.class);

            if (!Integer.valueOf(1).equals(ping)) {
                return Health.down()
                        .withDetail("database", "PostgreSQL")
                        .withDetail("error", "unexpected query result")
                        .build();
            }

            List<String> extensions = jdbcTemplate.query(
                    "SELECT extversion FROM pg_extension WHERE extname = 'vector'",
                    (rs, rowNum) -> rs.getString("extversion"));

            if (extensions.isEmpty()) {
                return Health.down()
                        .withDetail("database", "PostgreSQL")
                        .withDetail("validationQuery", "SELECT 1")
                        .withDetail("pgvector", "missing")
                        .withDetail("error", "pgvector extension is not installed")
                        .build();
            }

            return Health.up()
                    .withDetail("database", "PostgreSQL")
                    .withDetail("validationQuery", "SELECT 1")
                    .withDetail("pgvector", extensions.get(0))
                    .build();

        } catch (Exception e) {
            return Health.down(e)
                    .withDetail("database", "PostgreSQL")
                    .withDetail("error", e.getMessage())
                    .build();
        }
    }
}
