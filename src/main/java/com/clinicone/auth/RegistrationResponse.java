package com.clinicone.auth;

import java.util.UUID;

public record RegistrationResponse(UUID accountId, String email, String fullName) {
}
