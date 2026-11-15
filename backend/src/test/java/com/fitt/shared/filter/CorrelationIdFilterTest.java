package com.fitt.shared.filter;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationIdFilterTest {

    private String idEchoedFor(String incoming) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/health");
        if (incoming != null) {
            request.addHeader("X-Request-ID", incoming);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        new CorrelationIdFilter().doFilter(request, response, new MockFilterChain());
        return response.getHeader("X-Request-ID");
    }

    @Test
    void plainShortId_isKeptAsIs() throws Exception {
        assertThat(idEchoedFor("abc-123_DEF.9")).isEqualTo("abc-123_DEF.9");
    }

    @Test
    void missingId_isReplacedWithGeneratedUuid() throws Exception {
        assertThat(idEchoedFor(null)).matches("[0-9a-f-]{36}");
    }

    @Test
    void oversizedOrUnsafeId_isReplacedWithGeneratedUuid() throws Exception {
        assertThat(idEchoedFor("a".repeat(65))).matches("[0-9a-f-]{36}");
        assertThat(idEchoedFor("id with spaces & <tags>")).matches("[0-9a-f-]{36}");
        assertThat(idEchoedFor("id\u0007bell")).matches("[0-9a-f-]{36}");
    }
}
