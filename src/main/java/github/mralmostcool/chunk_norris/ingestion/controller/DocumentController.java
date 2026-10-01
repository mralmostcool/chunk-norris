package github.mralmostcool.chunk_norris.ingestion.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import github.mralmostcool.chunk_norris.common.exceptions.DocumentNotFoundException;
import github.mralmostcool.chunk_norris.ingestion.dto.DocumentResponse;
import github.mralmostcool.chunk_norris.ingestion.model.Document;
import github.mralmostcool.chunk_norris.ingestion.repository.DocumentRepository;
import github.mralmostcool.chunk_norris.ingestion.service.IngestionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
@Tag(name = "Document Ingestion", description = "API endpoints for uploading, querying, and managing ingested documents")
public class DocumentController {

    private final IngestionService ingestionService;
    private final DocumentRepository documentRepository;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload and ingest a document into vector store")
    public ResponseEntity<DocumentResponse> uploadDocument(@RequestParam("file") MultipartFile file) {
        Document ingested = ingestionService.ingest(file);
        return ResponseEntity.status(HttpStatus.CREATED).body(DocumentResponse.from(ingested));
    }

    @GetMapping
    @Operation(summary = "List all documents and their ingestion status")
    public ResponseEntity<List<DocumentResponse>> listDocuments() {
        List<DocumentResponse> responses = documentRepository.findAll().stream()
                .map(DocumentResponse::from)
                .toList();
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get document ingestion details by ID")
    public ResponseEntity<DocumentResponse> getDocument(@PathVariable("id") UUID id) {
        Document document = documentRepository.findById(id)
                .orElseThrow(() -> new DocumentNotFoundException(id.toString()));
        return ResponseEntity.ok(DocumentResponse.from(document));
    }
}
