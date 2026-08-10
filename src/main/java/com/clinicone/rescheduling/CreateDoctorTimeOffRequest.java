package com.clinicone.rescheduling;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

public record CreateDoctorTimeOffRequest(
        @NotNull UUID doctorId,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        @NotBlank @Size(min = 10, max = 500) String reason
) {
}
