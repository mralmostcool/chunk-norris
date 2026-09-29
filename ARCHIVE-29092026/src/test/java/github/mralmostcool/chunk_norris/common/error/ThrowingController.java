package github.mralmostcool.chunk_norris.common.error;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import github.mralmostcool.chunk_norris.common.exceptions.DocumentNotFoundException;
import github.mralmostcool.chunk_norris.common.exceptions.DuplicateDocumentException;
import github.mralmostcool.chunk_norris.common.exceptions.LlmUnavailableException;
import github.mralmostcool.chunk_norris.common.exceptions.SessionNotFoundException;
import github.mralmostcool.chunk_norris.common.exceptions.UnsupportedFileTypeException;

@RestController
public class ThrowingController {

    @GetMapping("/test/doc-not-found")
    void docNotFound() { throw new DocumentNotFoundException("abc"); }

    @GetMapping("/test/session-not-found")
    void sessionNotFound() { throw new SessionNotFoundException("xyz"); }

    @GetMapping("/test/llm-down")
    void llmDown() { throw new LlmUnavailableException("Ollama unreachable", new RuntimeException()); }

    @GetMapping("/test/bad-type")
    void badType() { throw new UnsupportedFileTypeException("application/zip"); }

    @GetMapping("/test/duplicate")
    void duplicate() { throw new DuplicateDocumentException("doc-1"); }

    @GetMapping("/test/boom")
    void boom() { throw new IllegalStateException("secret internal detail"); }
}
