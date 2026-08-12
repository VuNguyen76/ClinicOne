package com.clinicone.reception;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReceptionCheckInRequest(
        @NotBlank String roomCode,
        @NotBlank @Size(min = 3, max = 250) String reason) {
}
