package com.clinicone.auth;

import java.time.Instant;
import java.util.UUID;
import java.util.List;

public record StaffLoginResponse(String accessToken, String tokenType, Instant expiresAt,
                                 UUID staffId, String fullName, String role, List<String> roles) {
    public StaffLoginResponse(String accessToken, String tokenType, Instant expiresAt,
                              UUID staffId, String fullName, String role) {
        this(accessToken, tokenType, expiresAt, staffId, fullName, role, List.of(role));
    }
}
