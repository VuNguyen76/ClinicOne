package com.clinicone.config;

import com.clinicone.auth.AuthException;
import com.clinicone.auth.OtpException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
        request = new MockHttpServletRequest();
        request.setAttribute(TraceIdFilter.REQUEST_ATTRIBUTE, "trace-test-123");
    }

    @Test
    void handlesOtpExceptionWithTraceIdAndRetryAfter() {
        var response = handler.handle(
                new OtpException(HttpStatus.TOO_MANY_REQUESTS, "OTP_RATE_LIMITED", "Thử lại sau.", 60),
                request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(response.getBody()).containsEntry("code", "OTP_RATE_LIMITED");
        assertThat(response.getBody()).containsEntry("retryAfterSeconds", 60L);

        Map<?, ?> error = (Map<?, ?>) response.getBody().get("error");
        assertThat(error.get("traceId")).isEqualTo("trace-test-123");
        assertThat(error.get("code")).isEqualTo("OTP_RATE_LIMITED");
    }

    @Test
    void handlesAuthException() {
        var response = handler.handle(
                new AuthException(HttpStatus.FORBIDDEN, "FORBIDDEN_ACTION", "Không có quyền."),
                request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).containsEntry("code", "FORBIDDEN_ACTION");
    }

    @Test
    void handlesOptimisticLockingFailure() {
        var response = handler.handle(
                new ObjectOptimisticLockingFailureException("Record", "id-1"),
                request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).containsEntry("code", "MEDICAL_RECORD_VERSION_CONFLICT");
    }

    @Test
    void handlesHttpMessageNotReadable() {
        var response = handler.handle(
                new HttpMessageNotReadableException("Invalid JSON", new MockHttpInputMessage(new byte[0])),
                request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("code", "REQUEST_BODY_INVALID");
    }

    @Test
    void handlesMissingServletRequestParameter() {
        var response = handler.handle(
                new MissingServletRequestParameterException("specialty", "String"),
                request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("code", "MISSING_REQUIRED_PARAM");
    }

    @Test
    void handlesGenericException() {
        var response = handler.handle(
                new RuntimeException("Unexpected DB crash"),
                request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).containsEntry("code", "INTERNAL_SERVER_ERROR");
        assertThat(response.getBody()).containsEntry("message", "Hệ thống đang gặp sự cố. Vui lòng thử lại sau.");
    }
}
