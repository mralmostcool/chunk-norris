package github.mralmostcool.chunk_norris.common.exceptions;

import org.springframework.http.HttpStatus;

public class SessionNotFoundException extends RagException {
    public SessionNotFoundException(String id) {
        super(HttpStatus.NOT_FOUND, "SESSION_NOT_FOUND", "Conversation not found: " + id);
    }
}
