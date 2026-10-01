package github.mralmostcool.chunk_norris.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
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
import github.mralmostcool.chunk_norris.ingestion.service.IngestionService;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class DocumentUpdateIntegrationTest {

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

    @Autowired
    private IngestionService ingestionService;

    @Test
    @DisplayName("Update document: old content is no longer retrievable and new content is")
    void updateDocument_oldContentGoneAndNewContentRetrievable() throws Exception {
        String oldPhrase = "Unique astrophysics black hole singularity event horizon " + UUID.randomUUID();
        MockMultipartFile initialFile = new MockMultipartFile(
                "file",
                "original.txt",
                "text/plain",
                oldPhrase.getBytes(StandardCharsets.UTF_8));

        // 1. Initial upload
        MvcResult uploadResult = mvc.perform(multipart("/api/documents").file(initialFile))
                .andExpect(status().isCreated())
                .andReturn();

        String idStr = JsonPath.read(uploadResult.getResponse().getContentAsString(), "$.id");
        UUID docId = UUID.fromString(idStr);

        // 2. Similarity search for old content succeeds
        SearchRequest searchOld = SearchRequest.builder()
                .query(oldPhrase)
                .filterExpression(new FilterExpressionBuilder().eq("docId", docId.toString()).build())
                .topK(1)
                .build();
        List<Document> oldMatches = vectorStore.similaritySearch(searchOld);
        assertThat(oldMatches).isNotEmpty();

        // 3. Update document with new content
        String newPhrase = "Unique marine biology coral reef biodiversity underwater ecology " + UUID.randomUUID();
        MockMultipartFile updatedFile = new MockMultipartFile(
                "file",
                "revised.txt",
                "text/plain",
                newPhrase.getBytes(StandardCharsets.UTF_8));

        mvc.perform(multipart("/api/documents/" + docId)
                .file(updatedFile)
                .with(req -> { req.setMethod("PUT"); return req; }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(idStr))
                .andExpect(jsonPath("$.filename").value("revised.txt"))
                .andExpect(jsonPath("$.status").value("READY"));

        // 4. Assert old content is NO LONGER retrievable
        SearchRequest searchOldWithThreshold = SearchRequest.builder()
                .query(oldPhrase)
                .filterExpression(new FilterExpressionBuilder().eq("docId", docId.toString()).build())
                .similarityThreshold(0.7)
                .topK(1)
                .build();
        List<Document> oldMatchesAfter = vectorStore.similaritySearch(searchOldWithThreshold);
        assertThat(oldMatchesAfter).isEmpty();

        // Also assert that whatever is stored under docId does not contain the old content
        List<Document> rawMatches = vectorStore.similaritySearch(searchOld);
        boolean containsOldText = rawMatches.stream()
                .anyMatch(d -> d.getText() != null && d.getText().contains("astrophysics"));
        assertThat(containsOldText).isFalse();

        // 5. Assert new content IS retrievable
        SearchRequest searchNew = SearchRequest.builder()
                .query(newPhrase)
                .filterExpression(new FilterExpressionBuilder().eq("docId", docId.toString()).build())
                .similarityThreshold(0.7)
                .topK(1)
                .build();
        List<Document> newMatches = vectorStore.similaritySearch(searchNew);
        assertThat(newMatches).isNotEmpty();
        assertThat(newMatches.get(0).getText()).contains("marine biology");
        assertThat(newMatches.get(0).getMetadata()).containsEntry("docId", docId.toString());


        // Clean up
        ingestionService.deleteDocument(docId);
    }
}
