package com.clinicone.auth;

import com.clinicone.config.TraceIdFilter;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OtpExceptionHandlerTest {
    @Test
    void includesTraceIdInTheVersionedErrorEnvelopeAndKeepsLegacyFields() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(TraceIdFilter.REQUEST_ATTRIBUTE, "trace-42");

        var response = new OtpExceptionHandler().handle(
                new OtpException(HttpStatus.TOO_MANY_REQUESTS, "OTP_RATE_LIMITED", "Thử lại sau.", 60),
                request);

        assertThat(response.getBody()).containsEntry("code", "OTP_RATE_LIMITED");
        assertThat(response.getBody()).containsKey("error");
        Map<?, ?> error = (Map<?, ?>) response.getBody().get("error");
        assertThat(error.get("traceId")).isEqualTo("trace-42");
        assertThat(error.get("code")).isEqualTo("OTP_RATE_LIMITED");
    }
}
