package github.mralmostcool.chunk_norris.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.jayway.jsonpath.JsonPath;

import github.mralmostcool.chunk_norris.ingestion.repository.DocumentRepository;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class DocumentDeleteIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    @Autowired
    private MockMvc mvc;

    @Autowired
    private VectorStore vectorStore;

    @Autowired
    private DocumentRepository documentRepository;

    @Test
    @DisplayName("Delete document: vectors and row removed; similarity search returns nothing; second delete returns 404")
    void deleteDocument_removesVectorsAndSecondDeleteReturns404() throws Exception {
        String uniqueContent = "Unique delete test content quantum physics and particle acceleration " + UUID.randomUUID();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "delete-test.txt",
                "text/plain",
                uniqueContent.getBytes(StandardCharsets.UTF_8));

        // 1. Upload document
        MvcResult uploadResult = mvc.perform(multipart("/api/documents").file(file))
                .andExpect(status().isCreated())
                .andReturn();

        String idStr = JsonPath.read(uploadResult.getResponse().getContentAsString(), "$.id");
        UUID docId = UUID.fromString(idStr);

        // 2. Similarity search confirms presence
        SearchRequest searchBefore = SearchRequest.builder()
                .query(uniqueContent)
                .filterExpression(new FilterExpressionBuilder().eq("docId", docId.toString()).build())
                .topK(1)
                .build();
        List<Document> matchesBefore = vectorStore.similaritySearch(searchBefore);
        assertThat(matchesBefore).isNotEmpty();

        // 3. First DELETE /api/documents/{id} -> 204 No Content
        mvc.perform(delete("/api/documents/" + docId))
                .andExpect(status().isNoContent());

        // 4. Assert DB row gone
        assertThat(documentRepository.findById(docId)).isEmpty();

        // 5. Assert similarity search returns nothing
        List<Document> matchesAfter = vectorStore.similaritySearch(searchBefore);
        assertThat(matchesAfter).isEmpty();

        // 6. Second DELETE /api/documents/{id} -> 404 Not Found
        mvc.perform(delete("/api/documents/" + docId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("DOCUMENT_NOT_FOUND"));
    }
}
