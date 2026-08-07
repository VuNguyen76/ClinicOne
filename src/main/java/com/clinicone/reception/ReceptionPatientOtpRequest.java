package com.clinicone.reception;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ReceptionPatientOtpRequest(
        @NotBlank @Pattern(regexp = "0\\d{9}") String phone
) {
}
