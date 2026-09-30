package github.mralmostcool.chunk_norris.vectorstore;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "spring.ai.vectorstore.pgvector.initialize-schema=true")
@Testcontainers
class VectorStoreRoundTripIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    @Autowired
    private VectorStore vectorStore;

    @Test
    @DisplayName("VectorStore round-trip: insert 3 docs, similarity search, delete by filter, assert gone")
    void vectorStoreRoundTrip() {
        
        // 1. Add 3 docs with distinct metadata
        Document docDb = Document.builder()
                .text("PostgreSQL supports pgvector extension for similarity search.")
                .metadata("topic", "database")
                .metadata("docId", "doc-db")
                .build();
        
        Document docDog = Document.builder()
                .text("Golden retrievers love swimming and playing fetch with tennis balls.")
                .metadata("topic", "animals")
                .metadata("docId", "doc-dog")
                .build();

        Document docCooking = Document.builder()
                .text("Sourdough bread requires wild yeast fermentation and high hydration dough.")
                .metadata("topic", "cooking")
                .metadata("docId", "doc-food")
                .build();

        vectorStore.add(List.of(docDb, docDog, docCooking));

        // 2. Similarity search matching docDb
        SearchRequest searchRequest = SearchRequest.builder()
                .query("relational database vector embeddings")
                .topK(1)
                .build();
        List<Document> matches = vectorStore.similaritySearch(searchRequest);
        assertThat(matches).isNotEmpty();
        assertThat(matches.get(0).getMetadata()).containsEntry("docId", "doc-db");


        // 3. Delete by Filter.Expression
        Filter.Expression filter = new FilterExpressionBuilder()
                .eq("topic", "database")
                .build();
        vectorStore.delete(filter);


        // 4. Assert gone
        SearchRequest searchAfterDelete = SearchRequest.builder()
                .query("relational database vector embeddings")
                .filterExpression(new FilterExpressionBuilder().eq("topic", "database").build())
                .topK(1)
                .build();

        List<Document> filteredResults = vectorStore.similaritySearch(searchAfterDelete);
        assertThat(filteredResults).isEmpty();

    }
}
