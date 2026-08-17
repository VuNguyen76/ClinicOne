package com.clinicone.validation;

import com.clinicone.auth.AuthException;
import org.springframework.http.HttpStatus;

/** Normalizes and validates idempotency keys at non-HTTP service boundaries. */
public final class IdempotencyKeys {
    public static final int MAX_LENGTH = 80;

    private IdempotencyKeys() {
    }

    public static String optional(String requestKey) {
        if (requestKey == null || requestKey.isBlank()) {
            return null;
        }
        return validateLength(requestKey.trim());
    }

    public static String required(String requestKey, String requiredMessage) {
        String normalized = optional(requestKey);
        if (normalized == null) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "IDEMPOTENCY_KEY_REQUIRED", requiredMessage);
        }
        return normalized;
    }

    private static String validateLength(String normalized) {
        if (normalized.length() > MAX_LENGTH) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "IDEMPOTENCY_KEY_INVALID",
                    "Khóa chống trùng không được dài quá 80 ký tự.");
        }
        return normalized;
    }
}
