package com.clinicone.auth;

import com.clinicone.config.TraceIdFilter;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MethodArgumentNotValidException;
import jakarta.servlet.http.HttpServletRequest;

import java.util.Map;
import java.util.List;

@RestControllerAdvice
public class OtpExceptionHandler {

    @ExceptionHandler(OtpException.class)
    ResponseEntity<Map<String, Object>> handle(OtpException exception, HttpServletRequest request) {
        Map<String, Object> body = Map.of(
                "code", exception.getCode(),
                "message", exception.getMessage(),
                "retryAfterSeconds", exception.getRetryAfterSeconds(),
                "error", envelope(exception.getCode(), exception.getMessage(),
                        Map.of("retryAfterSeconds", exception.getRetryAfterSeconds()), request)
        );
        return ResponseEntity.status(exception.getStatus()).body(body);
    }

    @ExceptionHandler(AuthException.class)
    ResponseEntity<Map<String, Object>> handle(AuthException exception, HttpServletRequest request) {
        return ResponseEntity.status(exception.getStatus()).body(Map.of(
                "code", exception.getCode(),
                "message", exception.getMessage(),
                "error", envelope(exception.getCode(), exception.getMessage(), Map.of(), request)
        ));
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ResponseEntity<Map<String, Object>> handle(ObjectOptimisticLockingFailureException exception,
                                               HttpServletRequest request) {
        String code = "MEDICAL_RECORD_VERSION_CONFLICT";
        String message = "Dữ liệu đã được cập nhật ở một cửa sổ khác. Hãy tải lại trang trước khi tiếp tục.";
        return ResponseEntity.status(409).body(Map.of(
                "code", code,
                "message", message,
                "error", envelope(code, message, Map.of(), request)
        ));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, Object>> handle(MethodArgumentNotValidException exception,
                                                HttpServletRequest request) {
        List<Map<String, String>> errors = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> Map.of("field", error.getField(), "message",
                        error.getDefaultMessage() == null ? "Giá trị không hợp lệ." : error.getDefaultMessage()))
                .toList();
        String code = "VALIDATION_ERROR";
        String message = "Dữ liệu nhập chưa hợp lệ.";
        return ResponseEntity.badRequest().body(Map.of(
                "code", code,
                "message", message,
                "errors", errors,
                "error", envelope(code, message, Map.of("errors", errors), request)
        ));
    }

    private Map<String, Object> envelope(String code, String message, Map<String, Object> details,
                                         HttpServletRequest request) {
        Object traceId = request.getAttribute(TraceIdFilter.REQUEST_ATTRIBUTE);
        if (traceId == null) traceId = request.getHeader(TraceIdFilter.HEADER);
        return Map.of(
                "code", code,
                "message", message,
                "details", details,
                "traceId", traceId == null ? "unknown" : traceId.toString()
        );
    }
}
