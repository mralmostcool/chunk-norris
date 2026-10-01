package github.mralmostcool.chunk_norris.retrieval;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.stereotype.Component;

@Component
public class CitationIndexer {

    public List<RetrievedChunk> assignCitationIndices(List<RetrievedChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return Collections.emptyList();
        }

        List<RetrievedChunk> indexed = new ArrayList<>(chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            RetrievedChunk chunk = chunks.get(i);
            int citationIndex = i + 1;
            indexed.add(chunk != null ? chunk.withCitationIndex(citationIndex) : null);
        }
        return Collections.unmodifiableList(indexed);
    }
}
