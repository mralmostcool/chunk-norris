package github.mralmostcool.chunk_norris.retrieval;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class RetrievalConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class);

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(RetrievalConfig.class)
    static class TestConfig {
    }

    @Test
    @DisplayName("Binds default retrieval properties when none provided")
    void testDefaultValues() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(RetrievalConfig.class);
            RetrievalConfig config = context.getBean(RetrievalConfig.class);
            assertThat(config.topK()).isEqualTo(4);
            assertThat(config.similarityThreshold()).isEqualTo(0.5);
        });
    }

    @Test
    @DisplayName("Overrides retrieval config per environment properties")
    void testEnvironmentOverrides() {
        runner.withPropertyValues(
                "rag.retrieval.top-k=8",
                "rag.retrieval.similarity-threshold=0.75"
        ).run(context -> {
            assertThat(context).hasSingleBean(RetrievalConfig.class);
            RetrievalConfig config = context.getBean(RetrievalConfig.class);
            assertThat(config.topK()).isEqualTo(8);
            assertThat(config.similarityThreshold()).isEqualTo(0.75);
        });
    }

    @Test
    @DisplayName("Custom constructor fallback keeps valid defaults on non-positive values")
    void testConstructorFallback() {
        RetrievalConfig config = new RetrievalConfig(0, 0.0);
        assertThat(config.topK()).isEqualTo(4);
        assertThat(config.similarityThreshold()).isEqualTo(0.5);
    }
}
