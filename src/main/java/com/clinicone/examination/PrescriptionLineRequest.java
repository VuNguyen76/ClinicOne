package com.clinicone.examination;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record PrescriptionLineRequest(
        UUID medicationId,
        @NotBlank @Size(max = 200) String medicationName,
        @NotBlank @Size(max = 100) String dosage,
        @Min(1) @Max(999) Integer quantity,
        @NotBlank @Size(max = 500) String instructions
) {
}
