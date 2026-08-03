package com.clinicone.auth;

public record RequestOtpResponse(long expiresInSeconds, long retryAfterSeconds) {
}
