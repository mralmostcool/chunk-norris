package github.mralmostcool.chunk_norris.ingestion;

import java.util.List;

import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

@Component
public class DocumentReaderFactory {

    List<Document> read(Resource resource) {
        return new TikaDocumentReader(resource).read();
    }

}
