package com.clinicone.auth;

import java.util.UUID;

public record PatientProfileResponse(
        UUID accountId,
        String phone,
        String fullName,
        AccountStatus status,
        boolean mustChangePassword
) {
}
