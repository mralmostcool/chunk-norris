package github.mralmostcool.chunk_norris.ingestion.validation;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.tika.Tika;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartFile;

import github.mralmostcool.chunk_norris.common.exceptions.EmptyFileException;
import github.mralmostcool.chunk_norris.common.exceptions.UnsupportedFileTypeException;
import github.mralmostcool.chunk_norris.config.RagProperties;

@Component
public class UploadValidator {

    public static final String MIME_PDF = "application/pdf";
    public static final String MIME_DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    public static final String MIME_TXT = "text/plain";
    public static final String MIME_HTML = "text/html";

    private static final Map<String, Set<String>> EXTENSION_TO_MIME = Map.of(
            "pdf", Set.of(MIME_PDF),
            "docx", Set.of(MIME_DOCX),
            "txt", Set.of(MIME_TXT),
            "html", Set.of(MIME_HTML),
            "htm", Set.of(MIME_HTML));

    private static final Set<String> ALLOWED_MIME_TYPES = Set.of(
            MIME_PDF,
            MIME_DOCX,
            MIME_TXT,
            MIME_HTML);

    private final RagProperties ragProperties;
    private final Tika tika;

    @org.springframework.beans.factory.annotation.Autowired
    public UploadValidator(RagProperties ragProperties) {
        this.ragProperties = ragProperties;
        this.tika = new Tika();
    }

    public UploadValidator(RagProperties ragProperties, Tika tika) {
        this.ragProperties = ragProperties;
        this.tika = tika;
    }

    public void validate(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() == 0) {
            throw new EmptyFileException("File is empty or missing");
        }

        if (file.getSize() > ragProperties.maxUploadBytes()) {
            throw new MaxUploadSizeExceededException(ragProperties.maxUploadBytes());
        }

        String rawFilename = file.getOriginalFilename();
        String extension = StringUtils.getFilenameExtension(rawFilename);
        if (extension == null || extension.isBlank()) {
            throw new UnsupportedFileTypeException("Missing file extension");
        }

        String ext = extension.toLowerCase(Locale.ROOT);
        if (!EXTENSION_TO_MIME.containsKey(ext)) {
            throw new UnsupportedFileTypeException("Extension ." + ext + " not permitted");
        }

        String detectedMime;
        try (InputStream is = new BufferedInputStream(file.getInputStream())) {
            detectedMime = tika.detect(is, rawFilename);
        } catch (IOException e) {
            throw new UnsupportedFileTypeException("Unable to inspect file MIME type");
        }

        if (detectedMime == null) {
            throw new UnsupportedFileTypeException("Unknown MIME type");
        }

        String normalizedMime = detectedMime.split(";")[0].trim().toLowerCase(Locale.ROOT);

        if (!ALLOWED_MIME_TYPES.contains(normalizedMime)) {
            throw new UnsupportedFileTypeException(normalizedMime);
        }

        Set<String> allowedMimesForExt = EXTENSION_TO_MIME.get(ext);
        if (!allowedMimesForExt.contains(normalizedMime)) {
            throw new UnsupportedFileTypeException("MIME type " + normalizedMime + " mismatches extension ." + ext);
        }
    }
}
