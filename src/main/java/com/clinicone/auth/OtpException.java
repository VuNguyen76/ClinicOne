package com.clinicone.auth;

import org.springframework.http.HttpStatus;

public class OtpException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final long retryAfterSeconds;

    public OtpException(HttpStatus status, String code, String message) {
        this(status, code, message, 0);
    }

    public OtpException(HttpStatus status, String code, String message, long retryAfterSeconds) {
        super(message);
        this.status = status;
        this.code = code;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public HttpStatus getStatus() { return status; }
    public String getCode() { return code; }
    public long getRetryAfterSeconds() { return retryAfterSeconds; }
}
