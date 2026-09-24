package github.mralmostcool.chunk_norris.ingestion;

import java.util.List;

import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.stereotype.Service;

import github.mralmostcool.chunk_norris.config.RagProperties;

@Service
public class ChunkingService {

    private final TokenTextSplitter splitter;

    ChunkingService(RagProperties props) {
        this.splitter = TokenTextSplitter.builder()
                .withChunkSize(props.chunkSize())
                .build();
    }

    List<Document> chunk(List<Document> documents) {
        return splitter.apply(documents);
    }

}
