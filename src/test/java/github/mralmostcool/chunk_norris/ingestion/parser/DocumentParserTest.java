package github.mralmostcool.chunk_norris.ingestion.parser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.InputStreamResource;

import github.mralmostcool.chunk_norris.common.exceptions.DocumentParsingException;

class DocumentParserTest {

    private final DocumentParser documentParser = new DocumentParser();

    private static final Path FIXTURE_DIR = Paths.get("src/test/resources/fixtures");
    private static final Path PDF_PATH = FIXTURE_DIR.resolve("sample.pdf");
    private static final Path DOCX_PATH = FIXTURE_DIR.resolve("sample.docx");

    @BeforeAll
    static void setupFixtures() throws IOException {
        Files.createDirectories(FIXTURE_DIR);

        // Generate sample.pdf if missing
        if (!Files.exists(PDF_PATH)) {
            try (PDDocument doc = new PDDocument()) {
                PDPage page = new PDPage();
                doc.addPage(page);
                try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                    cs.beginText();
                    cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    cs.newLineAtOffset(100, 700);
                    cs.showText("This is a PDF test document for Chunk Norris ingestion.");
                    cs.endText();
                }
                doc.save(PDF_PATH.toFile());
            }
        }

        // Generate sample.docx if missing
        if (!Files.exists(DOCX_PATH)) {
            try (XWPFDocument doc = new XWPFDocument()) {
                XWPFParagraph p = doc.createParagraph();
                XWPFRun r = p.createRun();
                r.setText("This is a DOCX test document for Chunk Norris ingestion.");
                try (FileOutputStream fos = new FileOutputStream(DOCX_PATH.toFile())) {
                    doc.write(fos);
                }
            }
        }
    }

    @Test
    @DisplayName("Parse TXT fixture to non-empty text")
    void parse_txtFixture() {
        ClassPathResource resource = new ClassPathResource("fixtures/sample.txt");
        List<Document> docs = documentParser.parse(resource);

        assertThat(docs).isNotEmpty();
        assertThat(docs.get(0).getText()).contains("Chunk Norris is a powerful RAG application");
    }

    @Test
    @DisplayName("Parse HTML fixture to non-empty text")
    void parse_htmlFixture() {
        ClassPathResource resource = new ClassPathResource("fixtures/sample.html");
        List<Document> docs = documentParser.parse(resource);

        assertThat(docs).isNotEmpty();
        assertThat(docs.get(0).getText()).contains("Overview of Chunk Norris");
    }

    @Test
    @DisplayName("Parse PDF fixture to non-empty text")
    void parse_pdfFixture() {
        List<Document> docs = documentParser.parse(new FileSystemResource(PDF_PATH));

        assertThat(docs).isNotEmpty();
        assertThat(docs.get(0).getText()).contains("This is a PDF test document for Chunk Norris ingestion");
    }

    @Test
    @DisplayName("Parse DOCX fixture to non-empty text")
    void parse_docxFixture() {
        List<Document> docs = documentParser.parse(new FileSystemResource(DOCX_PATH));

        assertThat(docs).isNotEmpty();
        assertThat(docs.get(0).getText()).contains("This is a DOCX test document for Chunk Norris ingestion");
    }

    @Test
    @DisplayName("Parse empty input stream throws DocumentParsingException")
    void parse_emptyStream_throwsException() {
        InputStreamResource resource = new InputStreamResource(new ByteArrayInputStream(new byte[0]), "empty.txt");
        assertThatThrownBy(() -> documentParser.parse(resource))
                .isInstanceOf(DocumentParsingException.class);
    }

    @Test
    @DisplayName("Parse null input throws IllegalArgumentException")
    void parse_null_throwsException() {
        assertThatThrownBy(() -> documentParser.parse((org.springframework.core.io.Resource) null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
