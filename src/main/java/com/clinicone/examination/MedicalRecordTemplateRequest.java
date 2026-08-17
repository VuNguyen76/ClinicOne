package com.clinicone.examination;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record MedicalRecordTemplateRequest(
        @NotBlank @Size(max = 40) String code,
        @NotBlank @Size(max = 160) String name,
        @NotBlank @Size(max = 120) String specialty,
        UUID clinicServiceId,
        @Size(max = 500) String description,
        @NotBlank @Size(min = 3, max = 20000) String fieldDefinition
) {
}
