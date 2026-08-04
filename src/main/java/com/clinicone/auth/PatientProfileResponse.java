package com.clinicone.auth;

import java.util.UUID;
import java.time.LocalDate;

public record PatientProfileResponse(
        UUID accountId,
        String phone,
        String fullName,
        LocalDate dateOfBirth,
        String gender,
        String address,
        AccountStatus status,
        boolean mustChangePassword
) {
}
