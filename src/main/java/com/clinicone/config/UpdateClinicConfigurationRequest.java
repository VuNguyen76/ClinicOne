package com.clinicone.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateClinicConfigurationRequest(
        @NotBlank @Size(max = 160) String unitName,
        @NotBlank @Size(max = 160) String departmentName,
        @Min(5) @Max(30) int holdMinutes,
        @Min(0) @Max(72) int cancellationThresholdHours
) {
}
