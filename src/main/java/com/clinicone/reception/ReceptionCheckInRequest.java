package com.clinicone.reception;

import jakarta.validation.constraints.NotBlank;

public record ReceptionCheckInRequest(@NotBlank String roomCode) {
}
