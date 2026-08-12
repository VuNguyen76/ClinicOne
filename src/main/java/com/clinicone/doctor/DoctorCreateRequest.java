package com.clinicone.doctor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DoctorCreateRequest(
        @NotBlank @Size(min = 3, max = 80) String username,
        @NotBlank @Size(min = 2, max = 200) String fullName,
        @NotBlank @Size(min = 6, max = 72) String password
) {
}
