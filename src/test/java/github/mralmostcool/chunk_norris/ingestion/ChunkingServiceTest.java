package github.mralmostcool.chunk_norris.ingestion;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.core.io.ClassPathResource;

import github.mralmostcool.chunk_norris.config.RagProperties;

class ChunkingServiceTest {

    private static final int CHUNK_SIZE = 300;

    // match the order and types of YOUR RagProperties record
    private final ChunkingService service = new ChunkingService(new RagProperties(CHUNK_SIZE, 4, 0.5, "unused"));

    private Document longDocument() {
        String sample = new DocumentReaderFactory()
                .read(new ClassPathResource("sample-docs/sample.md"))
                .get(0).getText();
        // repeat the sample so there is plenty to split
        return new Document(sample.repeat(10));
    }

    @Test
    void longInputProducesMoreThanOneChunk() {
        List<Document> chunks = service.chunk(List.of(longDocument()));

        assertTrue(chunks.size() > 1, "expected several chunks but got " + chunks.size());
    }

    @Test
    void noChunkExceedsCharacterCeiling() {
        List<Document> chunks = service.chunk(List.of(longDocument()));

        // English text averages roughly 4 characters per token; 6 is a generous ceiling
        int ceiling = CHUNK_SIZE * 6;

        for (Document chunk : chunks) {
            assertTrue(chunk.getText().length() <= ceiling,
                    "chunk too long: " + chunk.getText().length() + " chars");
        }
    }

}