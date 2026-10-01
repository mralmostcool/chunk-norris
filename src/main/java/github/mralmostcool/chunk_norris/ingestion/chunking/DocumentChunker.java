package github.mralmostcool.chunk_norris.ingestion.chunking;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingRegistry;
import com.knuddels.jtokkit.api.EncodingType;
import com.knuddels.jtokkit.api.IntArrayList;

import github.mralmostcool.chunk_norris.config.RagProperties;

@Component
public class DocumentChunker {

    private final int chunkSize;
    private final int chunkOverlap;
    private final Encoding encoding;

    public DocumentChunker(RagProperties ragProperties) {
        this.chunkSize = Math.max(1, ragProperties.chunkSize());
        this.chunkOverlap = Math.max(0, Math.min(ragProperties.chunkOverlap(), this.chunkSize - 1));
        EncodingRegistry registry = Encodings.newDefaultEncodingRegistry();
        this.encoding = registry.getEncoding(EncodingType.CL100K_BASE);
    }

    public List<Document> chunk(UUID docId, String filename, List<Document> documents) {
        if (docId == null) {
            throw new IllegalArgumentException("docId must not be null");
        }
        if (filename == null || filename.isBlank()) {
            throw new IllegalArgumentException("filename must not be blank");
        }
        if (documents == null || documents.isEmpty()) {
            return List.of();
        }

        List<Document> allChunks = new ArrayList<>();
        int globalChunkIndex = 0;

        for (Document doc : documents) {
            String text = doc.getText();
            if (text == null || text.isBlank()) {
                continue;
            }

            Object page = doc.getMetadata().getOrDefault("page_number",
                    doc.getMetadata().getOrDefault("page", 1));

            IntArrayList tokens = encoding.encode(text);
            int totalTokens = tokens.size();

            if (totalTokens <= chunkSize) {
                Map<String, Object> metadata = new HashMap<>(doc.getMetadata());
                metadata.put("docId", docId.toString());
                metadata.put("filename", filename);
                metadata.put("chunkIndex", globalChunkIndex++);
                metadata.put("page", page);

                allChunks.add(Document.builder()
                        .text(text)
                        .metadata(metadata)
                        .build());
            } else {
                int step = chunkSize - chunkOverlap;
                for (int start = 0; start < totalTokens; start += step) {
                    int end = Math.min(start + chunkSize, totalTokens);
                    IntArrayList subTokens = new IntArrayList(end - start);
                    for (int j = start; j < end; j++) {
                        subTokens.add(tokens.get(j));
                    }

                    String chunkText = encoding.decode(subTokens);
                    Map<String, Object> metadata = new HashMap<>(doc.getMetadata());
                    metadata.put("docId", docId.toString());
                    metadata.put("filename", filename);
                    metadata.put("chunkIndex", globalChunkIndex++);
                    metadata.put("page", page);

                    allChunks.add(Document.builder()
                            .text(chunkText)
                            .metadata(metadata)
                            .build());

                    if (end >= totalTokens) {
                        break;
                    }
                }
            }
        }

        return allChunks;
    }

    public int getChunkSize() {
        return chunkSize;
    }

    public int getChunkOverlap() {
        return chunkOverlap;
    }
}
