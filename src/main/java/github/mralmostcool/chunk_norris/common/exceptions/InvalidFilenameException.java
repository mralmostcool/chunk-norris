package github.mralmostcool.chunk_norris.common.exceptions;

import org.springframework.http.HttpStatus;

public class InvalidFilenameException extends RagException {
    public InvalidFilenameException(String message) {
        super(HttpStatus.BAD_REQUEST, "INVALID_FILENAME", message);
    }
}
