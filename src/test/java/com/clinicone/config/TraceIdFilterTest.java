package com.clinicone.config;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockFilterChain;

import static org.assertj.core.api.Assertions.assertThat;

class TraceIdFilterTest {
    @Test
    void generatesTraceIdAndEchoesItOnTheResponse() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/health");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = new MockFilterChain();

        new TraceIdFilter().doFilter(request, response, chain);

        assertThat(response.getHeader("X-Trace-Id")).matches("[A-Za-z0-9_-]{20,64}");
        assertThat(request.getAttribute(TraceIdFilter.REQUEST_ATTRIBUTE))
                .isEqualTo(response.getHeader("X-Trace-Id"));
    }

    @Test
    void preservesASafeIncomingTraceId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/health");
        request.addHeader("X-Trace-Id", "client-trace-42");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new TraceIdFilter().doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeader("X-Trace-Id")).isEqualTo("client-trace-42");
    }
}
