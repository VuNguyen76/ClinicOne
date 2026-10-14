package com.clinicone.doctor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateDoctorProfileRequest(@NotBlank @Size(min = 2, max = 200) String fullName) {
}
