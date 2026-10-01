package github.mralmostcool.chunk_norris.ingestion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import github.mralmostcool.chunk_norris.common.exceptions.DocumentParsingException;
import github.mralmostcool.chunk_norris.common.exceptions.DuplicateDocumentException;
import github.mralmostcool.chunk_norris.ingestion.checksum.ChecksumService;
import github.mralmostcool.chunk_norris.ingestion.chunking.DocumentChunker;
import github.mralmostcool.chunk_norris.ingestion.embedding.VectorBatchService;
import github.mralmostcool.chunk_norris.ingestion.model.Document;
import github.mralmostcool.chunk_norris.ingestion.model.DocumentStatus;
import github.mralmostcool.chunk_norris.ingestion.parser.DocumentParser;
import github.mralmostcool.chunk_norris.ingestion.repository.DocumentRepository;
import github.mralmostcool.chunk_norris.ingestion.storage.FileStorageService;
import github.mralmostcool.chunk_norris.ingestion.validation.UploadValidator;

@ExtendWith(MockitoExtension.class)
class IngestionServiceTest {

    @Mock
    private UploadValidator uploadValidator;

    @Mock
    private ChecksumService checksumService;

    @Mock
    private FileStorageService fileStorageService;

    @Mock
    private DocumentParser documentParser;

    @Mock
    private DocumentChunker documentChunker;

    @Mock
    private VectorBatchService vectorBatchService;

    @Mock
    private DocumentRepository documentRepository;

    private IngestionService ingestionService;

    private MockMultipartFile mockFile;
    private final String checksum = "abcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890";

    @BeforeEach
    void setUp() {
        ingestionService = new IngestionService(
                uploadValidator,
                checksumService,
                fileStorageService,
                documentParser,
                documentChunker,
                vectorBatchService,
                documentRepository);

        mockFile = new MockMultipartFile(
                "file",
                "test.pdf",
                "application/pdf",
                "dummy content".getBytes());
    }

    @Test
    @DisplayName("Happy path: orchestrates entire pipeline and returns READY document")
    void ingest_happyPath() {
        when(checksumService.calculateSha256(mockFile)).thenReturn(checksum);
        when(documentRepository.findByChecksumAndStatus(checksum, DocumentStatus.READY)).thenReturn(Optional.empty());
        when(documentRepository.findByChecksumAndStatus(checksum, DocumentStatus.PROCESSING)).thenReturn(Optional.empty());
        when(fileStorageService.sanitizeFilename("test.pdf")).thenReturn("test.pdf");

        Path fakePath = Paths.get("data/uploads/fake.pdf");
        when(fileStorageService.save(any(UUID.class), eq(mockFile))).thenReturn(fakePath);

        org.springframework.ai.document.Document parsed = org.springframework.ai.document.Document.builder().text("hello").build();
        when(documentParser.parse(fakePath)).thenReturn(List.of(parsed));

        org.springframework.ai.document.Document chunk = org.springframework.ai.document.Document.builder().text("hello").build();
        when(documentChunker.chunk(any(UUID.class), eq("test.pdf"), anyList())).thenReturn(List.of(chunk));

        when(documentRepository.insert(any(Document.class))).thenAnswer(inv -> inv.getArgument(0));

        Document readyDoc = Document.builder()
                .id(UUID.randomUUID())
                .filename("test.pdf")
                .contentType("application/pdf")
                .sizeBytes(100L)
                .checksum(checksum)
                .chunkCount(1)
                .status(DocumentStatus.READY)
                .uploadedAt(Instant.now())
                .build();
        when(documentRepository.findById(any(UUID.class))).thenReturn(Optional.of(readyDoc));

        Document result = ingestionService.ingest(mockFile);

        assertThat(result.status()).isEqualTo(DocumentStatus.READY);
        verify(uploadValidator).validate(mockFile);
        verify(fileStorageService).save(any(UUID.class), eq(mockFile));
        verify(documentParser).parse(fakePath);
        verify(documentChunker).chunk(any(UUID.class), eq("test.pdf"), anyList());
        verify(vectorBatchService).storeChunks(any(UUID.class), anyList());
    }

    @Test
    @DisplayName("Duplicate in READY state throws 409 DuplicateDocumentException and halts")
    void ingest_duplicateReady_throwsException() {
        when(checksumService.calculateSha256(mockFile)).thenReturn(checksum);
        UUID existingId = UUID.randomUUID();
        Document existingDoc = Document.builder().id(existingId).status(DocumentStatus.READY).build();
        when(documentRepository.findByChecksumAndStatus(checksum, DocumentStatus.READY)).thenReturn(Optional.of(existingDoc));

        assertThatThrownBy(() -> ingestionService.ingest(mockFile))
                .isInstanceOf(DuplicateDocumentException.class);

        verify(documentRepository, never()).insert(any(Document.class));
        verify(fileStorageService, never()).save(any(), any());
    }

    @Test
    @DisplayName("Failure at file save stage compensates: deletes file, deletes vectors, marks FAILED")
    void ingest_failureAtFileSave_compensates() {
        when(checksumService.calculateSha256(mockFile)).thenReturn(checksum);
        when(documentRepository.findByChecksumAndStatus(anyString(), any())).thenReturn(Optional.empty());
        when(fileStorageService.sanitizeFilename("test.pdf")).thenReturn("test.pdf");

        when(fileStorageService.save(any(UUID.class), eq(mockFile)))
                .thenThrow(new RuntimeException("Disk full"));

        assertThatThrownBy(() -> ingestionService.ingest(mockFile))
                .isInstanceOf(RuntimeException.class);

        // Compensation verification: leaves no orphan vectors or files
        verify(vectorBatchService).deleteByDocId(any(UUID.class));
        verify(fileStorageService).delete(any(UUID.class));
        verify(documentRepository).updateStatus(any(UUID.class), eq(DocumentStatus.FAILED), eq("Disk full"));
    }

    @Test
    @DisplayName("Failure at parse stage compensates: deletes file, deletes vectors, marks FAILED")
    void ingest_failureAtParse_compensates() {
        when(checksumService.calculateSha256(mockFile)).thenReturn(checksum);
        when(documentRepository.findByChecksumAndStatus(anyString(), any())).thenReturn(Optional.empty());
        when(fileStorageService.sanitizeFilename("test.pdf")).thenReturn("test.pdf");

        Path fakePath = Paths.get("data/uploads/fake.pdf");
        when(fileStorageService.save(any(UUID.class), eq(mockFile))).thenReturn(fakePath);
        when(documentParser.parse(fakePath)).thenThrow(new DocumentParsingException("Corrupt PDF"));

        assertThatThrownBy(() -> ingestionService.ingest(mockFile))
                .isInstanceOf(DocumentParsingException.class);

        verify(vectorBatchService).deleteByDocId(any(UUID.class));
        verify(fileStorageService).delete(any(UUID.class));
        verify(documentRepository).updateStatus(any(UUID.class), eq(DocumentStatus.FAILED), eq("Corrupt PDF"));
    }

    @Test
    @DisplayName("Failure at chunking stage compensates: deletes file, deletes vectors, marks FAILED")
    void ingest_failureAtChunk_compensates() {
        when(checksumService.calculateSha256(mockFile)).thenReturn(checksum);
        when(documentRepository.findByChecksumAndStatus(anyString(), any())).thenReturn(Optional.empty());
        when(fileStorageService.sanitizeFilename("test.pdf")).thenReturn("test.pdf");

        Path fakePath = Paths.get("data/uploads/fake.pdf");
        when(fileStorageService.save(any(UUID.class), eq(mockFile))).thenReturn(fakePath);
        when(documentParser.parse(fakePath)).thenReturn(List.of(org.springframework.ai.document.Document.builder().text("x").build()));
        when(documentChunker.chunk(any(UUID.class), anyString(), anyList()))
                .thenThrow(new IllegalArgumentException("Invalid chunk size"));

        assertThatThrownBy(() -> ingestionService.ingest(mockFile))
                .isInstanceOf(RuntimeException.class);

        verify(vectorBatchService).deleteByDocId(any(UUID.class));
        verify(fileStorageService).delete(any(UUID.class));
        verify(documentRepository).updateStatus(any(UUID.class), eq(DocumentStatus.FAILED), eq("Invalid chunk size"));
    }

    @Test
    @DisplayName("Failure at embed/store stage compensates: deletes file, deletes vectors, marks FAILED")
    void ingest_failureAtEmbed_compensates() {
        when(checksumService.calculateSha256(mockFile)).thenReturn(checksum);
        when(documentRepository.findByChecksumAndStatus(anyString(), any())).thenReturn(Optional.empty());
        when(fileStorageService.sanitizeFilename("test.pdf")).thenReturn("test.pdf");

        Path fakePath = Paths.get("data/uploads/fake.pdf");
        when(fileStorageService.save(any(UUID.class), eq(mockFile))).thenReturn(fakePath);
        when(documentParser.parse(fakePath)).thenReturn(List.of(org.springframework.ai.document.Document.builder().text("x").build()));
        when(documentChunker.chunk(any(UUID.class), anyString(), anyList()))
                .thenReturn(List.of(org.springframework.ai.document.Document.builder().text("x").build()));

        doThrow(new RuntimeException("Vector store unavailable"))
                .when(vectorBatchService).storeChunks(any(UUID.class), anyList());

        assertThatThrownBy(() -> ingestionService.ingest(mockFile))
                .isInstanceOf(RuntimeException.class);

        verify(vectorBatchService).deleteByDocId(any(UUID.class));
        verify(fileStorageService).delete(any(UUID.class));
        verify(documentRepository).updateStatus(any(UUID.class), eq(DocumentStatus.FAILED), eq("Vector store unavailable"));
    }
}
