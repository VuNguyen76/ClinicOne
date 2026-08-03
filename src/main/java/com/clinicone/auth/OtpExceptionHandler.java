package com.clinicone.auth;

import org.springframework.http.ResponseEntity;
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
}
