package github.mralmostcool.chunk_norris.ingestion;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.core.io.ClassPathResource;

class DocumentReaderFactoryTest {

    private final DocumentReaderFactory factory = new DocumentReaderFactory();

    @Test
    void readsTextFromMarkdownFile() {
        var resource = new ClassPathResource("sample-docs/sample.md");

        List<Document> docs = factory.read(resource);

        assertFalse(docs.isEmpty());
        String text = docs.get(0).getText();
        assertTrue(text.contains("Gerald"));
        assertTrue(text.contains("Tomas Weber"));
    }
}