package github.mralmostcool.chunk_norris.ingestion.validation;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import github.mralmostcool.chunk_norris.common.exceptions.EmptyFileException;
import github.mralmostcool.chunk_norris.common.exceptions.UnsupportedFileTypeException;
import github.mralmostcool.chunk_norris.config.RagProperties;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UploadValidatorTest {

    private UploadValidator validator;
    private final long maxBytes = 1024 * 1024; // 1 MB

    @BeforeEach
    void setUp() {
        RagProperties properties = new RagProperties(300, 50, 4, 0.5, 10, maxBytes, "data/uploads");
        validator = new UploadValidator(properties);
    }

    @Test
    void validate_validPdf_success() {
        byte[] pdfContent = "%PDF-1.5 fake pdf content %%EOF".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile("file", "doc.pdf", "application/pdf", pdfContent);

        assertThatCode(() -> validator.validate(file)).doesNotThrowAnyException();
    }

    @Test
    void validate_validTxt_success() {
        byte[] txtContent = "Hello plain text world".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile("file", "readme.txt", "text/plain", txtContent);

        assertThatCode(() -> validator.validate(file)).doesNotThrowAnyException();
    }

    @Test
    void validate_validHtml_success() {
        byte[] htmlContent = "<!DOCTYPE html><html><body><h1>Hello</h1></body></html>".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile("file", "index.html", "text/html", htmlContent);

        assertThatCode(() -> validator.validate(file)).doesNotThrowAnyException();
    }

    @Test
    void validate_validDocx_success() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            zos.putNextEntry(new ZipEntry("word/document.xml"));
            zos.write("<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:body><w:p><w:r><w:t>Docx Content</w:t></w:r></w:p></w:body></w:document>".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        MockMultipartFile file = new MockMultipartFile("file", "test.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", baos.toByteArray());

        assertThatCode(() -> validator.validate(file)).doesNotThrowAnyException();
    }

    @Test
    void validate_emptyFile_throwsEmptyFileException() {
        MockMultipartFile emptyFile = new MockMultipartFile("file", "empty.txt", "text/plain", new byte[0]);

        assertThatThrownBy(() -> validator.validate(emptyFile))
                .isInstanceOf(EmptyFileException.class);
    }

    @Test
    void validate_oversizedFile_throwsMaxUploadSizeExceededException() {
        byte[] largeContent = new byte[(int) maxBytes + 10];
        MockMultipartFile file = new MockMultipartFile("file", "large.txt", "text/plain", largeContent);

        assertThatThrownBy(() -> validator.validate(file))
                .isInstanceOf(MaxUploadSizeExceededException.class);
    }

    @Test
    void validate_disallowedExtension_throwsUnsupportedFileTypeException() {
        byte[] content = "pretend image".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile("file", "pic.png", "image/png", content);

        assertThatThrownBy(() -> validator.validate(file))
                .isInstanceOf(UnsupportedFileTypeException.class);
    }

    @Test
    void validate_spoofedExtension_throwsUnsupportedFileTypeException() {
        // Plain text named as .pdf -> Tika detects text/plain mismatching .pdf
        byte[] plainText = "Not a real PDF file".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile("file", "spoof.pdf", "application/pdf", plainText);

        assertThatThrownBy(() -> validator.validate(file))
                .isInstanceOf(UnsupportedFileTypeException.class);
    }
}
