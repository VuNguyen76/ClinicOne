package com.clinicone.medication;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateMedicationRequest(
        @NotBlank @Size(max = 50) String code,
        @NotBlank @Size(max = 200) String name,
        @Size(max = 100) String category,
        @Size(max = 255) String specialties,
        @Size(max = 150) String defaultDosage,
        @Size(max = 500) String defaultInstructions,
        @Size(max = 50) String unit
) {
    public CreateMedicationRequest(String code, String name) {
        this(code, name, null, null, null, null, null);
    }
}
