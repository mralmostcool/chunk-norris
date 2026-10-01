package github.mralmostcool.chunk_norris.retrieval;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RetrievalServiceTest {

    @Mock
    private VectorStore vectorStore;

    private RetrievalConfig retrievalConfig;
    private RetrievalService retrievalService;

    @BeforeEach
    void setUp() {
        retrievalConfig = new RetrievalConfig(4, 0.6);
        retrievalService = new RetrievalService(vectorStore, retrievalConfig, new MetadataFilterBuilder());
    }

    @Test
    @DisplayName("Blank or null query immediately returns empty list without calling vectorStore")
    void testBlankQueryReturnsEmptyList() {
        assertThat(retrievalService.retrieve(null)).isEmpty();
        assertThat(retrievalService.retrieve("")).isEmpty();
        assertThat(retrievalService.retrieve("   ")).isEmpty();

        verify(vectorStore, never()).similaritySearch(any(SearchRequest.class));
    }

    @Test
    @DisplayName("retrieve uses topK and similarityThreshold from RetrievalConfig by default")
    void testRetrieveUsesDefaults() {
        Document doc = Document.builder()
                .text("Chunk text")
                .metadata(Map.of("docId", "doc-1"))
                .score(0.85)
                .build();
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(doc));

        List<RetrievedChunk> results = retrievalService.retrieve("test query");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).text()).isEqualTo("Chunk text");
        assertThat(results.get(0).score()).isEqualTo(0.85);

        ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(vectorStore).similaritySearch(captor.capture());
        SearchRequest captured = captor.getValue();
        assertThat(captured.getQuery()).isEqualTo("test query");
        assertThat(captured.getTopK()).isEqualTo(4);
        assertThat(captured.getSimilarityThreshold()).isEqualTo(0.6);
    }

    @Test
    @DisplayName("Drops chunks whose score is strictly below similarity threshold")
    void testFiltersOutWeakMatches() {
        Document strongDoc = Document.builder()
                .text("Strong match")
                .score(0.75)
                .build();
        Document weakDoc = Document.builder()
                .text("Weak match")
                .score(0.40)
                .build();

        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(strongDoc, weakDoc));

        List<RetrievedChunk> results = retrievalService.retrieve("any query");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).text()).isEqualTo("Strong match");
        assertThat(results.get(0).score()).isEqualTo(0.75);
    }

    @Test
    @DisplayName("Returns empty list when vector store returns nothing or all below threshold")
    void testReturnsEmptyWhenAllBelowThreshold() {
        Document weakDoc = Document.builder()
                .text("Weak match")
                .score(0.35)
                .build();
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(weakDoc));

        List<RetrievedChunk> results = retrievalService.retrieve("irrelevant query");
        assertThat(results).isEmpty();
    }

    @Test
    @DisplayName("Applies filter expression and custom topK / threshold overrides")
    void testCustomOverridesAndFilterExpression() {
        Filter.Expression filter = new FilterExpressionBuilder().eq("docId", "doc-xyz").build();

        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

        List<RetrievedChunk> results = retrievalService.retrieve("query", filter, 10, 0.8);
        assertThat(results).isEmpty();

        ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(vectorStore).similaritySearch(captor.capture());
        SearchRequest captured = captor.getValue();
        assertThat(captured.getTopK()).isEqualTo(10);
        assertThat(captured.getSimilarityThreshold()).isEqualTo(0.8);
        assertThat(captured.getFilterExpression()).isEqualTo(filter);
    }
}
