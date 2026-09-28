package github.mralmostcool.chunk_norris.common.exceptions;

import org.springframework.http.HttpStatus;

public class LlmUnavailableException extends RagException {
    public LlmUnavailableException(String message, Throwable cause) {
        super(HttpStatus.SERVICE_UNAVAILABLE, "LLM_UNAVAILABLE", message, cause);
    }
}
