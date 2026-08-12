package com.clinicone.reception;

import java.util.UUID;

public record ReceptionPatientRegistrationResponse(
        UUID accountId,
        String phone,
        String fullName,
        boolean mustChangePassword
) {
}
