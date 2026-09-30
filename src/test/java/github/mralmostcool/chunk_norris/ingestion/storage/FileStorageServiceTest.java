package github.mralmostcool.chunk_norris.ingestion.storage;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockMultipartFile;

import github.mralmostcool.chunk_norris.common.exceptions.InvalidFilenameException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileStorageServiceTest {

    @TempDir
    Path tempDir;

    private FileStorageService storageService;

    @BeforeEach
    void setUp() {
        storageService = new FileStorageService(tempDir);
    }

    @Test
    void sanitizeFilename_validName_returnsNormalizedBase() {
        String result = storageService.sanitizeFilename("document.pdf");
        assertThat(result).isEqualTo("document.pdf");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "../../etc/passwd",
            "..\\..\\windows\\system32\\cmd.exe",
            "../../../secret.txt",
            "dir/sub/sample.pdf",
            "dir\\sub\\sample.pdf"
    })
    void sanitizeFilename_pathTraversal_stripsDirectoryComponents(String raw) {
        String result = storageService.sanitizeFilename(raw);
        assertThat(result).doesNotContain("..").doesNotContain("/").doesNotContain("\\");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", ".", "..", "../../", "..\\..\\"})
    void sanitizeFilename_invalidOrEmpty_throwsException(String invalid) {
        assertThatThrownBy(() -> storageService.sanitizeFilename(invalid))
                .isInstanceOf(InvalidFilenameException.class);
    }

    @Test
    void save_validFile_persistsUnderDocIdDirectory() throws IOException {
        UUID docId = UUID.randomUUID();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test-document.pdf",
                "application/pdf",
                "pdf-data".getBytes(StandardCharsets.UTF_8));

        Path savedPath = storageService.save(docId, file);

        assertThat(Files.exists(savedPath)).isTrue();
        assertThat(savedPath.getParent()).isEqualTo(tempDir.resolve(docId.toString()));
        assertThat(savedPath.getFileName().toString()).isEqualTo("test-document.pdf");
        assertThat(Files.readString(savedPath)).isEqualTo("pdf-data");
    }

    @Test
    void delete_existingDoc_removesDirectoryCompletely() {
        UUID docId = UUID.randomUUID();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "report.txt",
                "text/plain",
                "report text".getBytes(StandardCharsets.UTF_8));

        storageService.save(docId, file);
        Path docDir = tempDir.resolve(docId.toString());
        assertThat(Files.exists(docDir)).isTrue();

        storageService.delete(docId);

        assertThat(Files.exists(docDir)).isFalse();
    }
}
