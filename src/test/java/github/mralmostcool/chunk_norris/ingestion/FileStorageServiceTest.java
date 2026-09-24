package github.mralmostcool.chunk_norris.ingestion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import github.mralmostcool.chunk_norris.config.RagProperties;

public class FileStorageServiceTest {

    @TempDir
    Path tempDir;

    private FileStorageService service() {
        return new FileStorageService(new RagProperties(300, 4, 0.5, tempDir.toString()));
    }

    @Test
    void savesFileInsideUploadDir() throws Exception {
        var file = new MockMultipartFile("file", "hello.txt", "text/plain", "hi".getBytes());
        Path saved = service().save(UUID.randomUUID(), file);
        assertTrue(Files.exists(saved));
        assertEquals("hi", Files.readString(saved));
        assertTrue(saved.startsWith(tempDir.toAbsolutePath().normalize()));
    }

    @Test
    void stripDirectoryPartsFromFileName() {
        var file = new MockMultipartFile("file", "../../evil.text", "text/plain", "x".getBytes());
        Path saved = service().save(UUID.randomUUID(), file);
        assertTrue(saved.startsWith(tempDir.toAbsolutePath().normalize()));
        assertTrue(saved.getFileName().toString().endsWith("evil.txt"));
    }

    @Test
    void rejectsDotDotAsFileName() {
        var file = new MockMultipartFile("file", "..", "text/plain", "x".getBytes());
        assertThrows(
                IllegalArgumentException.class,
                () -> service().save(UUID.randomUUID(), file));
    }

}
