package github.mralmostcool.chunk_norris.common.exceptions;

import org.springframework.http.HttpStatus;

public abstract class RagException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    protected RagException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    protected RagException(HttpStatus status, String code, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

}
