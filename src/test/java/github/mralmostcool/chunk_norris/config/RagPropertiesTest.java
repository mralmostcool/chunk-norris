package github.mralmostcool.chunk_norris.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RagPropertiesTest {

    @Test
    void testRagPropertiesRecord() {
        RagProperties props = new RagProperties(300, 50, 4, 0.5, 10, 20971520L, "data/uploads");

        assertThat(props.chunkSize()).isEqualTo(300);
        assertThat(props.chunkOverlap()).isEqualTo(50);
        assertThat(props.topK()).isEqualTo(4);
        assertThat(props.similarityThreshold()).isEqualTo(0.5);
        assertThat(props.memoryWindow()).isEqualTo(10);
        assertThat(props.maxUploadBytes()).isEqualTo(20971520L);
        assertThat(props.uploadDir()).isEqualTo("data/uploads");
    }
}
