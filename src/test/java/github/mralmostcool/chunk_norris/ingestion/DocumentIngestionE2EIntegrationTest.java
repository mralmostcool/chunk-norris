package github.mralmostcool.chunk_norris.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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

import github.mralmostcool.chunk_norris.ingestion.embedding.VectorBatchService;
import github.mralmostcool.chunk_norris.ingestion.model.Document;
import github.mralmostcool.chunk_norris.ingestion.model.DocumentStatus;
import github.mralmostcool.chunk_norris.ingestion.repository.DocumentRepository;
import github.mralmostcool.chunk_norris.ingestion.storage.FileStorageService;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class DocumentIngestionE2EIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    @Autowired
    private MockMvc mvc;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private VectorBatchService vectorBatchService;

    @Autowired
    private FileStorageService fileStorageService;

    @Test
    @DisplayName("End-to-End: Upload real small PDF, verify ingestion pipeline, database and vector store")
    void uploadRealPdf_e2e() throws Exception {
        Path pdfPath = Paths.get("src/test/resources/fixtures/sample.pdf");
        assertThat(Files.exists(pdfPath)).isTrue();

        byte[] pdfBytes = Files.readAllBytes(pdfPath);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "sample.pdf",
                "application/pdf",
                pdfBytes);

        // 1. POST /api/documents
        MvcResult result = mvc.perform(multipart("/api/documents").file(file))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.filename").value("sample.pdf"))
                .andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.chunkCount").isNumber())
                .andReturn();

        String idStr = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
        UUID docId = UUID.fromString(idStr);
        int chunkCount = JsonPath.read(result.getResponse().getContentAsString(), "$.chunkCount");

        assertThat(chunkCount).isGreaterThan(0);

        // 2. Verify Document in DB
        Document doc = documentRepository.findById(docId).orElseThrow();
        assertThat(doc.status()).isEqualTo(DocumentStatus.READY);
        assertThat(doc.chunkCount()).isEqualTo(chunkCount);

        // 3. Verify vectors persisted in vector_store
        int vectorCount = vectorBatchService.countByDocId(docId);
        assertThat(vectorCount).isEqualTo(chunkCount);

        // 4. GET /api/documents/{id}
        mvc.perform(get("/api/documents/" + docId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(idStr))
                .andExpect(jsonPath("$.status").value("READY"));

        // Cleanup
        vectorBatchService.deleteByDocId(docId);
        fileStorageService.delete(docId);
        documentRepository.deleteById(docId);
    }
}
