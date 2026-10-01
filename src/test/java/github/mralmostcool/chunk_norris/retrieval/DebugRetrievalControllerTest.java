package github.mralmostcool.chunk_norris.retrieval;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import github.mralmostcool.chunk_norris.common.error.GlobalExceptionHandler;
import github.mralmostcool.chunk_norris.common.logging.CorrelationIdFilter;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = DebugRetrievalController.class)
@Import({GlobalExceptionHandler.class, CorrelationIdFilter.class})
class DebugRetrievalControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private RetrievalService retrievalService;

    @Test
    @DisplayName("POST /api/debug/retrieve returns chunks with scores and citation indices")
    void testRetrieveSuccess() throws Exception {
        UUID docId = UUID.randomUUID();
        RetrievedChunk chunk1 = new RetrievedChunk("chunk-1", "Vector search content", Map.of("docId", docId.toString()), 0.88, 1);
        RetrievedChunk chunk2 = new RetrievedChunk("chunk-2", "Cosine similarity info", Map.of("docId", docId.toString()), 0.74, 2);

        when(retrievalService.retrieve(eq("vector search"), any(java.util.Collection.class), eq(5), eq(0.65)))
                .thenReturn(List.of(chunk1, chunk2));

        String requestJson = """
                {
                    "query": "vector search",
                    "docIds": ["%s"],
                    "topK": 5,
                    "similarityThreshold": 0.65
                }
                """.formatted(docId);

        mvc.perform(post("/api/debug/retrieve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id", is("chunk-1")))
                .andExpect(jsonPath("$[0].text", is("Vector search content")))
                .andExpect(jsonPath("$[0].score", is(0.88)))
                .andExpect(jsonPath("$[0].citationIndex", is(1)))
                .andExpect(jsonPath("$[1].id", is("chunk-2")))
                .andExpect(jsonPath("$[1].score", is(0.74)))
                .andExpect(jsonPath("$[1].citationIndex", is(2)));
    }

    @Test
    @DisplayName("POST /api/debug/retrieve with blank query returns 400 Bad Request")
    void testBlankQueryReturns400() throws Exception {
        String requestJson = """
                {
                    "query": "   "
                }
                """;

        mvc.perform(post("/api/debug/retrieve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isBadRequest());
    }
}
