package github.mralmostcool.chunk_norris.ingestion.checksum;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class ChecksumServiceTest {

    private final ChecksumService checksumService = new ChecksumService();

    // SHA-256("hello world")
    private static final String HELLO_WORLD_SHA256 = "b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9";

    @Test
    @DisplayName("calculateSha256 from byte array returns expected hex hash")
    void calculateSha256_byteArray() {
        byte[] input = "hello world".getBytes(StandardCharsets.UTF_8);
        String hash = checksumService.calculateSha256(input);
        assertThat(hash).isEqualTo(HELLO_WORLD_SHA256);
    }

    @Test
    @DisplayName("calculateSha256 from InputStream returns expected hex hash")
    void calculateSha256_inputStream() {
        ByteArrayInputStream is = new ByteArrayInputStream("hello world".getBytes(StandardCharsets.UTF_8));
        String hash = checksumService.calculateSha256(is);
        assertThat(hash).isEqualTo(HELLO_WORLD_SHA256);
    }

    @Test
    @DisplayName("calculateSha256 from MultipartFile returns expected hex hash")
    void calculateSha256_multipartFile() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.txt",
                "text/plain",
                "hello world".getBytes(StandardCharsets.UTF_8));
        String hash = checksumService.calculateSha256(file);
        assertThat(hash).isEqualTo(HELLO_WORLD_SHA256);
    }

    @Test
    @DisplayName("calculateSha256 with null byte array throws IllegalArgumentException")
    void calculateSha256_nullBytes() {
        assertThatThrownBy(() -> checksumService.calculateSha256((byte[]) null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("calculateSha256 with null or empty file throws IllegalArgumentException")
    void calculateSha256_emptyFile() {
        MockMultipartFile emptyFile = new MockMultipartFile("file", "empty.txt", "text/plain", new byte[0]);
        assertThatThrownBy(() -> checksumService.calculateSha256(emptyFile))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
