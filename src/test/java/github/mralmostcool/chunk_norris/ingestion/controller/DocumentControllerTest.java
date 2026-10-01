package github.mralmostcool.chunk_norris.ingestion.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import github.mralmostcool.chunk_norris.common.error.GlobalExceptionHandler;
import github.mralmostcool.chunk_norris.common.exceptions.DuplicateDocumentException;
import github.mralmostcool.chunk_norris.common.logging.CorrelationIdFilter;
import github.mralmostcool.chunk_norris.ingestion.model.Document;
import github.mralmostcool.chunk_norris.ingestion.model.DocumentStatus;
import github.mralmostcool.chunk_norris.ingestion.repository.DocumentRepository;
import github.mralmostcool.chunk_norris.ingestion.service.IngestionService;

@WebMvcTest(controllers = DocumentController.class)
@Import({GlobalExceptionHandler.class, CorrelationIdFilter.class})
class DocumentControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private IngestionService ingestionService;

    @MockitoBean
    private DocumentRepository documentRepository;

    @Test
    @DisplayName("POST /api/documents uploads file and returns 201 Created")
    void uploadDocument_returns201() throws Exception {
        UUID docId = UUID.randomUUID();
        MockMultipartFile file = new MockMultipartFile("file", "test.pdf", "application/pdf", "data".getBytes());

        Document doc = Document.builder()
                .id(docId)
                .filename("test.pdf")
                .contentType("application/pdf")
                .sizeBytes(4L)
                .checksum("checksum123")
                .chunkCount(1)
                .status(DocumentStatus.READY)
                .uploadedAt(Instant.now())
                .build();

        when(ingestionService.ingest(any())).thenReturn(doc);

        mvc.perform(multipart("/api/documents").file(file))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", is(docId.toString())))
                .andExpect(jsonPath("$.filename", is("test.pdf")))
                .andExpect(jsonPath("$.status", is("READY")))
                .andExpect(jsonPath("$.chunkCount", is(1)));
    }

    @Test
    @DisplayName("POST /api/documents duplicate file returns 409 Conflict")
    void uploadDocument_duplicate_returns409() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "test.pdf", "application/pdf", "data".getBytes());

        when(ingestionService.ingest(any())).thenThrow(new DuplicateDocumentException("existing-doc-id"));

        mvc.perform(multipart("/api/documents").file(file))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status", is(409)))
                .andExpect(jsonPath("$.code", is("DUPLICATE_DOCUMENT")));
    }

    @Test
    @DisplayName("GET /api/documents returns list of documents")
    void listDocuments_returns200() throws Exception {
        UUID docId1 = UUID.randomUUID();
        UUID docId2 = UUID.randomUUID();

        Document doc1 = Document.builder().id(docId1).filename("doc1.pdf").contentType("application/pdf").sizeBytes(10L).checksum("c1").chunkCount(1).status(DocumentStatus.READY).uploadedAt(Instant.now()).build();
        Document doc2 = Document.builder().id(docId2).filename("doc2.pdf").contentType("application/pdf").sizeBytes(20L).checksum("c2").chunkCount(2).status(DocumentStatus.READY).uploadedAt(Instant.now()).build();

        when(documentRepository.findAll()).thenReturn(List.of(doc1, doc2));

        mvc.perform(get("/api/documents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id", is(docId1.toString())))
                .andExpect(jsonPath("$[1].id", is(docId2.toString())));
    }

    @Test
    @DisplayName("GET /api/documents/{id} returns document details")
    void getDocument_found_returns200() throws Exception {
        UUID docId = UUID.randomUUID();
        Document doc = Document.builder().id(docId).filename("doc.pdf").contentType("application/pdf").sizeBytes(10L).checksum("c1").chunkCount(1).status(DocumentStatus.READY).uploadedAt(Instant.now()).build();

        when(documentRepository.findById(docId)).thenReturn(Optional.of(doc));

        mvc.perform(get("/api/documents/" + docId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(docId.toString())))
                .andExpect(jsonPath("$.filename", is("doc.pdf")));
    }

    @Test
    @DisplayName("GET /api/documents/{id} returns 404 when document not found")
    void getDocument_notFound_returns404() throws Exception {
        UUID docId = UUID.randomUUID();
        when(documentRepository.findById(docId)).thenReturn(Optional.empty());

        mvc.perform(get("/api/documents/" + docId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(jsonPath("$.code", is("DOCUMENT_NOT_FOUND")));
    }
}
