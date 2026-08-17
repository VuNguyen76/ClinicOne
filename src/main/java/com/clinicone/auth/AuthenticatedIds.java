package com.clinicone.auth;

import org.springframework.http.HttpStatus;

import java.util.UUID;

/** Parses account identifiers supplied by the authenticated principal. */
public final class AuthenticatedIds {
    private AuthenticatedIds() {
    }

    public static UUID patient(String accountId) {
        return parse(accountId, "Phiên đăng nhập không hợp lệ.");
    }

    public static UUID staff(String accountId) {
        return parse(accountId, "Phiên đăng nhập nhân viên không hợp lệ.");
    }

    public static UUID doctor(String accountId) {
        return parse(accountId, "Phiên đăng nhập bác sĩ không hợp lệ.");
    }

    private static UUID parse(String accountId, String message) {
        try {
            return UUID.fromString(accountId);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new AuthException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", message);
        }
    }
}
