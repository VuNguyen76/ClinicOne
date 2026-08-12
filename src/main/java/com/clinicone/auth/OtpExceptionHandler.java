package com.clinicone.auth;

import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class OtpExceptionHandler {

    @ExceptionHandler(OtpException.class)
    ResponseEntity<Map<String, Object>> handle(OtpException exception) {
        Map<String, Object> body = Map.of(
                "code", exception.getCode(),
                "message", exception.getMessage(),
                "retryAfterSeconds", exception.getRetryAfterSeconds()
        );
        return ResponseEntity.status(exception.getStatus()).body(body);
    }

    @ExceptionHandler(AuthException.class)
    ResponseEntity<Map<String, Object>> handle(AuthException exception) {
        return ResponseEntity.status(exception.getStatus()).body(Map.of(
                "code", exception.getCode(),
                "message", exception.getMessage()
        ));
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ResponseEntity<Map<String, Object>> handle(ObjectOptimisticLockingFailureException exception) {
        return ResponseEntity.status(409).body(Map.of(
                "code", "MEDICAL_RECORD_VERSION_CONFLICT",
                "message", "Dữ liệu đã được cập nhật ở một cửa sổ khác. Hãy tải lại trang trước khi tiếp tục."
        ));
    }
}
