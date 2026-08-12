package com.clinicone.config;

import java.time.Instant;
import java.util.UUID;

public record ClinicConfigurationResponse(UUID id, String unitName, String departmentName,
                                          int holdMinutes, int cancellationThresholdHours,
                                          String updatedBy, Instant updatedAt) {
    static ClinicConfigurationResponse from(ClinicConfiguration configuration) {
        return new ClinicConfigurationResponse(configuration.getId(), configuration.getUnitName(),
                configuration.getDepartmentName(), configuration.getHoldMinutes(),
                configuration.getCancellationThresholdHours(), configuration.getUpdatedBy(),
                configuration.getUpdatedAt());
    }
}
