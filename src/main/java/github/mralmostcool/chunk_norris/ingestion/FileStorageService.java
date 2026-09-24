package github.mralmostcool.chunk_norris.ingestion;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import github.mralmostcool.chunk_norris.config.RagProperties;

@Service
public class FileStorageService {

    private final Path uploadDir;

    FileStorageService(RagProperties props) {
        this.uploadDir = Path.of(props.uploadDir()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(uploadDir);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create upload directory: " + uploadDir, e);
        }
    }

    Path save(UUID documentId, MultipartFile file) {
        String raw = Objects.requireNonNullElse(file.getOriginalFilename(), "");
        String name = StringUtils.getFilename(StringUtils.cleanPath(raw));

        if (name == null || name.isBlank() || name.equals("..")) {
            throw new IllegalArgumentException("Invalid file name: '" + raw + "'");
        }

        Path target = uploadDir.resolve(documentId + "-" + name).normalize();
        if (!target.startsWith(uploadDir)) {
            throw new IllegalArgumentException("Invalid file path: " + name);
        }

        try (InputStream in = file.getInputStream()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to store file: " + name, e);
        }

        return target;
    }

}
