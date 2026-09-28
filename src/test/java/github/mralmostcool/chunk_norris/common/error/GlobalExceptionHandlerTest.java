package github.mralmostcool.chunk_norris.common.error;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.jayway.jsonpath.JsonPath;
import github.mralmostcool.chunk_norris.common.logging.CorrelationIdFilter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ThrowingController.class)
@Import({GlobalExceptionHandler.class, CorrelationIdFilter.class})
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void documentNotFound_returns404() throws Exception {
        mvc.perform(get("/test/doc-not-found"))
           .andExpect(status().isNotFound())
           .andExpect(jsonPath("$.status").value(404))
           .andExpect(jsonPath("$.code").value("DOCUMENT_NOT_FOUND"))
           .andExpect(jsonPath("$.message").value(containsString("abc")))
           .andExpect(jsonPath("$.path").value("/test/doc-not-found"))
           .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void sessionNotFound_returns404() throws Exception {
        mvc.perform(get("/test/session-not-found"))
           .andExpect(status().isNotFound())
           .andExpect(jsonPath("$.status").value(404))
           .andExpect(jsonPath("$.code").value("SESSION_NOT_FOUND"))
           .andExpect(jsonPath("$.message").value(containsString("xyz")))
           .andExpect(jsonPath("$.path").value("/test/session-not-found"))
           .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void llmDown_returns503() throws Exception {
        mvc.perform(get("/test/llm-down"))
           .andExpect(status().isServiceUnavailable())
           .andExpect(jsonPath("$.status").value(503))
           .andExpect(jsonPath("$.code").value("LLM_UNAVAILABLE"))
           .andExpect(jsonPath("$.message").value(containsString("Ollama unreachable")))
           .andExpect(jsonPath("$.path").value("/test/llm-down"))
           .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void badType_returns400() throws Exception {
        mvc.perform(get("/test/bad-type"))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.status").value(400))
           .andExpect(jsonPath("$.code").value("UNSUPPORTED_FILE_TYPE"))
           .andExpect(jsonPath("$.message").value(containsString("application/zip")))
           .andExpect(jsonPath("$.path").value("/test/bad-type"))
           .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void duplicate_returns409() throws Exception {
        mvc.perform(get("/test/duplicate"))
           .andExpect(status().isConflict())
           .andExpect(jsonPath("$.status").value(409))
           .andExpect(jsonPath("$.code").value("DUPLICATE_DOCUMENT"))
           .andExpect(jsonPath("$.message").value(containsString("doc-1")))
           .andExpect(jsonPath("$.path").value("/test/duplicate"))
           .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void unexpectedException_returns500_withoutLeakingDetails() throws Exception {
        mvc.perform(get("/test/boom"))
           .andExpect(status().isInternalServerError())
           .andExpect(jsonPath("$.status").value(500))
           .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
           .andExpect(jsonPath("$.message").value(not(containsString("secret"))));
    }

    @Test
    void errorBody_containsCorrelationId_matchingResponseHeader() throws Exception {
        MvcResult result = mvc.perform(get("/test/doc-not-found"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.correlationId").isNotEmpty())
            .andReturn();

        String headerId = result.getResponse().getHeader("X-Correlation-Id");
        String bodyId = JsonPath.read(result.getResponse().getContentAsString(), "$.correlationId");

        assertThat(bodyId).isEqualTo(headerId);
    }

    @Test
    void errorBody_echoesClientSuppliedCorrelationId() throws Exception {
        mvc.perform(get("/test/doc-not-found").header("X-Correlation-Id", "client-777"))
           .andExpect(jsonPath("$.correlationId").value("client-777"));
    }
}
