package github.mralmostcool.chunk_norris.common.exceptions;

import org.springframework.http.HttpStatus;

public class DocumentParsingException extends RagException {

    public DocumentParsingException(String message) {
        super(HttpStatus.UNPROCESSABLE_CONTENT, "DOCUMENT_PARSING_FAILED", message);
    }

    public DocumentParsingException(String message, Throwable cause) {
        super(HttpStatus.UNPROCESSABLE_CONTENT, "DOCUMENT_PARSING_FAILED", message, cause);
    }

}
