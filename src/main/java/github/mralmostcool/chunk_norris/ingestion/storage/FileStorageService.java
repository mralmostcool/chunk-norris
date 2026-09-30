package github.mralmostcool.chunk_norris.ingestion.storage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.util.FileSystemUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import github.mralmostcool.chunk_norris.common.exceptions.FileStorageException;
import github.mralmostcool.chunk_norris.common.exceptions.InvalidFilenameException;
import github.mralmostcool.chunk_norris.config.RagProperties;

@Service
public class FileStorageService {

    private final Path rootUploadDir;

    @org.springframework.beans.factory.annotation.Autowired
    public FileStorageService(RagProperties ragProperties) {
        this.rootUploadDir = Paths.get(ragProperties.uploadDir()).toAbsolutePath().normalize();
        init();
    }

    public FileStorageService(Path rootUploadDir) {
        this.rootUploadDir = rootUploadDir.toAbsolutePath().normalize();
        init();
    }

    private void init() {
        try {
            Files.createDirectories(this.rootUploadDir);
        } catch (IOException e) {
            throw new FileStorageException("Failed to initialize upload root directory: " + rootUploadDir, e);
        }
    }

    public String sanitizeFilename(String rawFilename) {
        if (rawFilename == null || rawFilename.isBlank()) {
            throw new InvalidFilenameException("Filename must not be empty");
        }

        String cleaned = StringUtils.cleanPath(rawFilename);
        Path path = Paths.get(cleaned).getFileName();
        if (path == null) {
            throw new InvalidFilenameException("Invalid filename: " + rawFilename);
        }

        String filename = path.toString().trim();
        if (filename.isEmpty() || filename.equals(".") || filename.equals("..")) {
            throw new InvalidFilenameException("Invalid filename: " + rawFilename);
        }

        if (filename.contains("\0") || filename.contains("/") || filename.contains("\\") || filename.contains("..")) {
            throw new InvalidFilenameException("Illegal path traversal sequence in filename: " + rawFilename);
        }

        return filename;
    }

    public Path save(UUID docId, MultipartFile file) {
        if (docId == null) {
            throw new IllegalArgumentException("Document ID must not be null");
        }
        String sanitizedFilename = sanitizeFilename(file.getOriginalFilename());
        Path docDir = rootUploadDir.resolve(docId.toString()).normalize();

        try {
            Files.createDirectories(docDir);
            Path destination = docDir.resolve(sanitizedFilename).normalize();

            if (!destination.startsWith(docDir)) {
                throw new InvalidFilenameException("Path traversal attempt detected: " + file.getOriginalFilename());
            }

            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, destination, StandardCopyOption.REPLACE_EXISTING);
            }
            return destination;
        } catch (IOException e) {
            throw new FileStorageException("Failed to store file: " + sanitizedFilename + " for doc: " + docId, e);
        }
    }

    public void delete(UUID docId) {
        if (docId == null) {
            return;
        }
        Path docDir = rootUploadDir.resolve(docId.toString()).normalize();
        if (!docDir.startsWith(rootUploadDir)) {
            throw new InvalidFilenameException("Invalid docId path resolution: " + docId);
        }
        try {
            if (Files.exists(docDir)) {
                FileSystemUtils.deleteRecursively(docDir);
            }
        } catch (IOException e) {
            throw new FileStorageException("Failed to delete directory for doc: " + docId, e);
        }
    }

    public Path resolvePath(UUID docId, String filename) {
        String safeName = sanitizeFilename(filename);
        Path docDir = rootUploadDir.resolve(docId.toString()).normalize();
        Path target = docDir.resolve(safeName).normalize();
        if (!target.startsWith(docDir)) {
            throw new InvalidFilenameException("Path traversal attempt detected: " + filename);
        }
        return target;
    }

    public Path getRootUploadDir() {
        return rootUploadDir;
    }
}
