package github.mralmostcool.chunk_norris;

import java.util.List;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/hello")
@Tag(name = "Vector Operations", description = "Endpoints for seeding, searching, and clearing vector store documents")
class HelloVectorController {

    /***
     * Vector Store is just a an object that will allow you to
     * perform read and write operations such as
     * ---> Manage Documents
     * ---> Query Documents
     * ---> Add Documents
     * ---> Delete Documents
     * 
     * these are to be used for AI Applications
     * such as Retrieval-Augmented Generation
     */

    /***
     * JdbcTemplate is Spring's core API for direct database interaction.
     * It handles connection management, statement execution, and exception translation.
     * 
     * Used here for direct SQL operations (such as clearing tables) when high-level
     * Spring AI VectorStore APIs do not expose bulk operations.
     */

    private final VectorStore vectorStore;
    private final JdbcTemplate jdbcTemplate;

    HelloVectorController(VectorStore vectorStore, JdbcTemplate jdbcTemplate) {
        this.vectorStore = vectorStore;
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostMapping("/seed")
    @Operation(summary = "Seed vector store", description = "Populates vector store with sample documents for similarity search")
    String seed() {
        vectorStore.add(
                List.of(
                        new Document("Golden retrievers love to fetch tennis balls."),
                        new Document("Spring Boot simplifies Java application setup."),
                        new Document("Cosine similarity measures the angle between two vectors."),
                        new Document("Postgres is a relational database with extensions like pgvector.")));
        return "seed";
    }

    @GetMapping("/search")
    @Operation(summary = "Similarity search", description = "Searches the vector store for top matches given a query string")
    List<Document> search(
            @Parameter(description = "Search query term", required = true)
            @RequestParam String q) {
        return vectorStore.similaritySearch(
                SearchRequest.builder().query(q).topK(2).build());
    }

    @DeleteMapping("/clear")
    @Operation(summary = "Delete all documents", description = "Deletes all entries from the vector store database")
    String clear() {
        jdbcTemplate.execute("DELETE FROM vector_store");
        return "all entries deleted";
    }

}


