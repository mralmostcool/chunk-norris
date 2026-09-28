package github.mralmostcool.chunk_norris.common.logging;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import github.mralmostcool.chunk_norris.common.error.GlobalExceptionHandler;
import github.mralmostcool.chunk_norris.common.error.ThrowingController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

@WebMvcTest(controllers = ThrowingController.class)
@Import({GlobalExceptionHandler.class, CorrelationIdFilter.class})
class CorrelationIdFilterTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void generatesIdWhenAbsent() throws Exception {
        mvc.perform(get("/test/doc-not-found"))
           .andExpect(header().exists("X-Correlation-Id"));
    }

    @Test
    void propagatesIncomingId() throws Exception {
        mvc.perform(get("/test/doc-not-found").header("X-Correlation-Id", "abc-123"))
           .andExpect(header().string("X-Correlation-Id", "abc-123"));
    }

    @Test
    void replacesInvalidIncomingId() throws Exception {
        mvc.perform(get("/test/doc-not-found").header("X-Correlation-Id", "bad id with spaces!"))
           .andExpect(header().string("X-Correlation-Id", not("bad id with spaces!")))
           .andExpect(header().string("X-Correlation-Id", matchesPattern("^[0-9a-f\\-]{36}$")));
    }

    @Test
    void mdcIsClearedAfterRequest() throws Exception {
        mvc.perform(get("/test/doc-not-found").header("X-Correlation-Id", "first-id"));
        assertThat(MDC.get("correlationId")).isNull();
    }
}
