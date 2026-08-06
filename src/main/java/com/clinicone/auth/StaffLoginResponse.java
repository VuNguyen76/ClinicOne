package com.clinicone.auth;

import java.time.Instant;
import java.util.UUID;

public record StaffLoginResponse(String accessToken, String tokenType, Instant expiresAt,
                                 UUID staffId, String fullName, String role) {
}
