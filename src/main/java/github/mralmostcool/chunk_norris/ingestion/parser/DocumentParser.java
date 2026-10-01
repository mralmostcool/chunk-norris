package github.mralmostcool.chunk_norris.ingestion.parser;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;

import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import github.mralmostcool.chunk_norris.common.exceptions.DocumentParsingException;

@Component
public class DocumentParser {

    public List<Document> parse(Resource resource) {
        if (resource == null) {
            throw new IllegalArgumentException("Resource must not be null");
        }
        try {
            TikaDocumentReader reader = new TikaDocumentReader(resource);
            List<Document> documents = reader.read();
            if (documents == null || documents.isEmpty()) {
                throw new DocumentParsingException("Parsed document produced no content for resource: " + resource.getFilename());
            }

            // Verify non-empty text across parsed documents
            boolean hasNonEmptyText = documents.stream()
                    .anyMatch(doc -> doc.getText() != null && !doc.getText().isBlank());

            if (!hasNonEmptyText) {
                throw new DocumentParsingException("Parsed document text is empty for resource: " + resource.getFilename());
            }

            return documents;
        } catch (DocumentParsingException e) {
            throw e;
        } catch (Exception e) {
            throw new DocumentParsingException("Failed to parse document for resource: " + resource.getFilename(), e);
        }
    }

    public List<Document> parse(Path path) {
        if (path == null) {
            throw new IllegalArgumentException("Path must not be null");
        }
        return parse(new FileSystemResource(path));
    }

    public List<Document> parse(InputStream inputStream, String filename) {
        if (inputStream == null) {
            throw new IllegalArgumentException("InputStream must not be null");
        }
        return parse(new InputStreamResource(inputStream, filename != null ? filename : "unnamed"));
    }
}
