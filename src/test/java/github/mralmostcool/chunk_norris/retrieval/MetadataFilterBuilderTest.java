package github.mralmostcool.chunk_norris.retrieval;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.vectorstore.filter.Filter;

import static org.assertj.core.api.Assertions.assertThat;

class MetadataFilterBuilderTest {

    private final MetadataFilterBuilder builder = new MetadataFilterBuilder();

    @Test
    @DisplayName("null or empty docIds return null filter expression")
    void testNullOrEmptyDocIds() {
        assertThat(builder.buildDocIdFilter(null)).isNull();
        assertThat(builder.buildDocIdFilter(Collections.emptyList())).isNull();
        assertThat(builder.buildDocIdFilter(Collections.singletonList(null))).isNull();
        assertThat(builder.buildDocIdFilter(Arrays.asList(null, null))).isNull();
    }

    @Test
    @DisplayName("Single docId produces valid IN expression")
    void testSingleDocId() {
        UUID id = UUID.randomUUID();
        Filter.Expression filter = builder.buildDocIdFilter(List.of(id));

        assertThat(filter).isNotNull();
        assertThat(filter.type()).isEqualTo(Filter.ExpressionType.IN);
        assertThat(filter.left()).isEqualTo(new Filter.Key("docId"));
        assertThat(filter.toString()).contains(id.toString());
    }

    @Test
    @DisplayName("Multiple docIds produce IN expression containing all ids without duplicates")
    void testMultipleDocIdsAndDeduplication() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();

        Filter.Expression filter = builder.buildDocIdFilter(List.of(id1, id2, id1));

        assertThat(filter).isNotNull();
        assertThat(filter.type()).isEqualTo(Filter.ExpressionType.IN);
        assertThat(filter.left()).isEqualTo(new Filter.Key("docId"));
        assertThat(filter.toString()).contains(id1.toString());
        assertThat(filter.toString()).contains(id2.toString());
    }
}
