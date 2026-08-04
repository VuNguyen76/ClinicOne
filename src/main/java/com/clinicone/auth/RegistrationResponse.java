package com.clinicone.auth;

import java.util.UUID;

public record RegistrationResponse(UUID accountId, String phone, String fullName) {
}
