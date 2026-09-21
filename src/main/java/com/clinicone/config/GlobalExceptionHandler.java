package com.clinicone.config;

import com.clinicone.auth.AuthException;
import com.clinicone.auth.OtpException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(OtpException.class)
    public ResponseEntity<Map<String, Object>> handle(OtpException exception, HttpServletRequest request) {
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
    public ResponseEntity<Map<String, Object>> handle(AuthException exception, HttpServletRequest request) {
        return ResponseEntity.status(exception.getStatus()).body(Map.of(
                "code", exception.getCode(),
                "message", exception.getMessage(),
                "error", envelope(exception.getCode(), exception.getMessage(), Map.of(), request)
        ));
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<Map<String, Object>> handle(ObjectOptimisticLockingFailureException exception,
                                                       HttpServletRequest request) {
        String code = "MEDICAL_RECORD_VERSION_CONFLICT";
        String message = "Dữ liệu đã được cập nhật ở một cửa sổ khác. Hãy tải lại trang trước khi tiếp tục.";
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "code", code,
                "message", message,
                "error", envelope(code, message, Map.of(), request)
        ));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handle(MethodArgumentNotValidException exception,
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

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, Object>> handle(ConstraintViolationException exception,
                                                       HttpServletRequest request) {
        List<Map<String, String>> errors = exception.getConstraintViolations().stream()
                .map(violation -> Map.of(
                        "field", violation.getPropertyPath() == null ? "" : violation.getPropertyPath().toString(),
                        "message", violation.getMessage() == null ? "Giá trị không hợp lệ." : violation.getMessage()))
                .toList();
        String code = "VALIDATION_ERROR";
        String message = "Tham số yêu cầu chưa hợp lệ.";
        return ResponseEntity.badRequest().body(Map.of(
                "code", code,
                "message", message,
                "errors", errors,
                "error", envelope(code, message, Map.of("errors", errors), request)
        ));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handle(HttpMessageNotReadableException exception,
                                                       HttpServletRequest request) {
        String code = "REQUEST_BODY_INVALID";
        String message = "Dữ liệu yêu cầu không đúng định dạng JSON.";
        return ResponseEntity.badRequest().body(Map.of(
                "code", code,
                "message", message,
                "error", envelope(code, message, Map.of(), request)
        ));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handle(MethodArgumentTypeMismatchException exception,
                                                       HttpServletRequest request) {
        String code = "PARAM_TYPE_MISMATCH";
        String paramName = exception.getName();
        String message = "Tham số '" + paramName + "' không đúng định dạng.";
        return ResponseEntity.badRequest().body(Map.of(
                "code", code,
                "message", message,
                "error", envelope(code, message, Map.of("parameter", paramName), request)
        ));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Map<String, Object>> handle(MissingServletRequestParameterException exception,
                                                       HttpServletRequest request) {
        String code = "MISSING_REQUIRED_PARAM";
        String paramName = exception.getParameterName();
        String message = "Thiếu tham số bắt buộc: " + paramName;
        return ResponseEntity.badRequest().body(Map.of(
                "code", code,
                "message", message,
                "error", envelope(code, message, Map.of("parameter", paramName), request)
        ));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> handle(HttpRequestMethodNotSupportedException exception,
                                                       HttpServletRequest request) {
        String code = "METHOD_NOT_ALLOWED";
        String message = "Phương thức " + exception.getMethod() + " không được hỗ trợ cho đường dẫn này.";
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(Map.of(
                "code", code,
                "message", message,
                "error", envelope(code, message, Map.of("method", exception.getMethod()), request)
        ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handle(Exception exception, HttpServletRequest request) {
        if (exception instanceof org.springframework.security.access.AccessDeniedException ade) {
            throw ade;
        }
        if (exception instanceof org.springframework.security.core.AuthenticationException ae) {
            throw ae;
        }
        log.error("Unhandled exception processing request [{}]: {}", request.getRequestURI(), exception.getMessage(), exception);
        String code = "INTERNAL_SERVER_ERROR";
        String message = "Hệ thống đang gặp sự cố. Vui lòng thử lại sau.";
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "code", code,
                "message", message,
                "error", envelope(code, message, Map.of(), request)
        ));
    }

    protected Map<String, Object> envelope(String code, String message, Map<String, Object> details,
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
