package com.clinicone.reason;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateClinicalReasonRequest(
        @NotBlank @Size(max = 20) String specialtyCode,
        @NotBlank @Size(max = 120) String specialtyName,
        @NotBlank @Size(min = 3, max = 200) String symptomName,
        Integer sortOrder
) {
}
