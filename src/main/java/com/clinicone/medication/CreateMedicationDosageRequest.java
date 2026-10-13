package com.clinicone.medication;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record CreateMedicationDosageRequest(
        @NotBlank @Size(max = 50) @Pattern(regexp = "[A-Za-z0-9_-]+") String code,
        @NotBlank @Size(max = 100) String unitName,
        @NotBlank @Size(max = 200) String dosageFormat,
        @Size(max = 255) String description,
        @PositiveOrZero int sortOrder
) {
}
