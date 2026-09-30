package github.mralmostcool.chunk_norris.common.exceptions;

import org.springframework.http.HttpStatus;

public class FileStorageException extends RagException {
    public FileStorageException(String message, Throwable cause) {
        super(HttpStatus.INTERNAL_SERVER_ERROR, "STORAGE_ERROR", message, cause);
    }

    public FileStorageException(String message) {
        super(HttpStatus.INTERNAL_SERVER_ERROR, "STORAGE_ERROR", message);
    }
}
