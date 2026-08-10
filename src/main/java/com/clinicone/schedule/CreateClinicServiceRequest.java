package com.clinicone.schedule;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record CreateClinicServiceRequest(
        @NotBlank @Size(max = 120) String name,
        @NotBlank @Size(max = 120) String specialty,
        @NotBlank @Size(max = 60) String visitType,
        @Min(5) @Max(120) int durationMinutes,
        @NotEmpty @Size(max = 100) List<@NotNull UUID> doctorIds,
        Boolean requiresMedicalRecord
) {
    public CreateClinicServiceRequest(String name, String specialty, String visitType, int durationMinutes,
                                      List<UUID> doctorIds) {
        this(name, specialty, visitType, durationMinutes, doctorIds, true);
    }
}
