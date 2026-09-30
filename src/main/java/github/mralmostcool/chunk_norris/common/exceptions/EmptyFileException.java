package github.mralmostcool.chunk_norris.common.exceptions;

import org.springframework.http.HttpStatus;

public class EmptyFileException extends RagException {
    public EmptyFileException() {
        super(HttpStatus.BAD_REQUEST, "EMPTY_FILE", "Uploaded file is empty");
    }

    public EmptyFileException(String message) {
        super(HttpStatus.BAD_REQUEST, "EMPTY_FILE", message);
    }
}
