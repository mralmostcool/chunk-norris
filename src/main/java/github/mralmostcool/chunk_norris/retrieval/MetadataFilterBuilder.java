package github.mralmostcool.chunk_norris.retrieval;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Component;

@Component
public class MetadataFilterBuilder {

    public Filter.Expression buildDocIdFilter(Collection<UUID> docIds) {
        if (docIds == null || docIds.isEmpty()) {
            return null;
        }

        List<String> validIds = docIds.stream()
                .filter(Objects::nonNull)
                .map(UUID::toString)
                .distinct()
                .toList();

        if (validIds.isEmpty()) {
            return null;
        }

        FilterExpressionBuilder builder = new FilterExpressionBuilder();
        return builder.in("docId", validIds.toArray(new Object[0])).build();
    }
}
