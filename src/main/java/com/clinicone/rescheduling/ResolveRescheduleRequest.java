package com.clinicone.rescheduling;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record ResolveRescheduleRequest(
        @NotNull @FutureOrPresent LocalDate appointmentDate,
        @NotNull LocalTime startTime,
        @NotBlank @Size(max = 120) String doctorName,
        UUID doctorId
) {
}
