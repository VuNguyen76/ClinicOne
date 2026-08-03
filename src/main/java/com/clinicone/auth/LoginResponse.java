package com.clinicone.auth;

import java.time.Instant;
import java.util.UUID;

public record LoginResponse(String accessToken, String tokenType, Instant expiresAt,
                            UUID accountId, String fullName, boolean mustChangePassword) {
}
